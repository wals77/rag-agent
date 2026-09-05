package com.rag.kb.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.rag.kb.config.ElasticsearchConfig;
import com.rag.kb.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 统一向量 + BM25 索引。
 * 索引 _id = chunkId，一个索引同时支撑 向量召回 / BM25 召回 / HyDE 召回。
 */
@Service
public class EsIndexService {

    private static final Logger log = LoggerFactory.getLogger(EsIndexService.class);

    public record EsDoc(String chunkId, String docId, String docName, Integer pageNum,
                        String chapterTitle, String text) {}

    /** 写入 ES 的文档对象，embedding 为向量字段 */
    public record EsStoredDoc(String chunkId, String docId, String docName, Integer pageNum,
                              String chapterTitle, String text, List<Float> embedding) {
        static EsStoredDoc from(EsDoc d, List<Float> embedding) {
            return new EsStoredDoc(d.chunkId(), d.docId(), d.docName(), d.pageNum(),
                    d.chapterTitle(), d.text(), embedding);
        }
    }

    public record EsHit(EsDoc doc, float score) {}

    private final ElasticsearchClient client;
    private final RagProperties props;

    public EsIndexService(ElasticsearchClient client, RagProperties props) {
        this.client = client;
        this.props = props;
    }

    public String indexName() {
        return props.getEsIndexName();
    }

    public boolean ping() {
        return ElasticsearchConfig.isReachable(client);
    }

    public synchronized void ensureIndex() throws IOException {
        String idx = indexName();
        if (client.indices().exists(e -> e.index(idx)).value()) {
            return;
        }
        TypeMapping mapping = TypeMapping.of(m -> m
                .properties("chunkId", Property.of(p -> p.keyword(k -> k)))
                .properties("docId", Property.of(p -> p.keyword(k -> k)))
                .properties("docName", Property.of(p -> p.keyword(k -> k)))
                .properties("pageNum", Property.of(p -> p.integer(i -> i)))
                .properties("chapterTitle", Property.of(p -> p.keyword(k -> k)))
                .properties("text", Property.of(p -> p.text(t -> t.analyzer("cjk"))))
                .properties("embedding", Property.of(p -> p.denseVector(
                        dv -> dv.index(true).similarity("cosine").dims(props.getEmbedding().getDim())))));
        CreateIndexRequest req = new CreateIndexRequest.Builder()
                .index(idx)
                .mappings(mapping)
                .build();
        client.indices().create(req);
        log.info("已创建 ES 索引 {}", idx);
    }

    public void bulkIndex(List<EsDoc> docs, List<Float> vector) throws IOException {
        if (docs.isEmpty()) return;
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (EsDoc doc : docs) {
            br.operations(op -> op.index(i -> i.index(indexName()).id(doc.chunkId())
                    .document(EsStoredDoc.from(doc, vector))));
        }
        BulkResponse resp = client.bulk(br.build());
        if (resp.errors()) {
            for (BulkResponseItem item : resp.items()) {
                if (item.error() != null) {
                    log.warn("ES bulk index error for {}: {}", item.id(), item.error().reason());
                }
            }
        }
    }

    /** 批量写入不同 chunk（各自携带向量），用于一次性全量入库 */
    public void bulkIndexWithVectors(List<IndexPayload> payloads) throws IOException {
        if (payloads.isEmpty()) return;
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (IndexPayload p : payloads) {
            br.operations(op -> op.index(i -> i.index(indexName()).id(p.doc.chunkId())
                    .document(EsStoredDoc.from(p.doc, p.vector))));
        }
        BulkResponse resp = client.bulk(br.build());
        if (resp.errors()) {
            for (BulkResponseItem item : resp.items()) {
                if (item.error() != null) {
                    log.warn("ES bulk index error for {}: {}", item.id(), item.error().reason());
                }
            }
        }
    }

    public record IndexPayload(EsDoc doc, List<Float> vector) {}

    public void deleteById(String chunkId) throws IOException {
        try {
            client.delete(d -> d.index(indexName()).id(chunkId));
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            if (e.status() != 404) throw e;
        }
    }

    public void deleteByDocId(String docId) throws IOException {
        client.deleteByQuery(d -> d.index(indexName())
                .query(q -> q.term(t -> t.field("docId").value(docId))));
    }

    public List<EsHit> knnSearch(List<Float> vector, int topK) throws IOException {
        SearchResponse<EsDoc> resp = client.search(s -> s
                        .index(indexName())
                        .knn(new KnnSearch.Builder()
                                .field("embedding")
                                .queryVector(vector)
                                .k((long) topK)
                                .numCandidates((long) topK * 10)
                                .build())
                        .size(topK),
                EsDoc.class);
        return toHits(resp);
    }

    public List<EsHit> bm25Search(String query, int topK) throws IOException {
        SearchResponse<EsDoc> resp = client.search(s -> s
                        .index(indexName())
                        .query(q -> q.match(m -> m.field("text").query(query)))
                        .size(topK),
                EsDoc.class);
        return toHits(resp);
    }

    private List<EsHit> toHits(SearchResponse<EsDoc> resp) {
        List<EsHit> hits = new ArrayList<>();
        if (resp.hits() == null || resp.hits().hits() == null) return hits;
        resp.hits().hits().forEach(h -> {
            EsDoc doc = h.source();
            if (doc == null) return;
            hits.add(new EsHit(doc, h.score() == null ? 0f : h.score().floatValue()));
        });
        return hits;
    }
}
