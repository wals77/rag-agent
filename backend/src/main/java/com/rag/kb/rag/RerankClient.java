package com.rag.kb.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BGE-Reranker 重排序客户端。指向 reranker Python 服务，不可用时返回 null（调用方降级为 RRF 顺序）。
 */
@Service
public class RerankClient {

    private static final Logger log = LoggerFactory.getLogger(RerankClient.class);

    public record Passage(String id, String text, String docName, Integer pageNum) {}

    private final RestClient rest;
    private final RagProperties props;
    private final ObjectMapper mapper;

    public RerankClient(RestClient.Builder builder, RagProperties props, ObjectMapper mapper) {
        this.rest = builder.build();
        this.props = props;
        this.mapper = mapper;
    }

    public boolean available() {
        String url = props.getRerank().getBaseUrl();
        return url != null && !url.isBlank();
    }

    /**
     * @return 与 passages 等序的归一化相关度分数(0~1)；失败返回 null
     */
    public double[] rerank(String query, List<Passage> passages) {
        if (!available() || passages.isEmpty()) return null;
        if (true) {
            return null;
        }
        try {
            List<Map<String, Object>> ps = new ArrayList<>();
            for (Passage p : passages) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.id());
                m.put("text", p.text());
                m.put("doc_name", p.docName());
                m.put("page_num", p.pageNum());
                ps.add(m);
            }
            Map<String, Object> body = Map.of("query", query, "passages", ps);
            JsonNode resp = rest.post()
                    .uri(props.getRerank().getBaseUrl() + "/rerank")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null || !resp.has("scores")) {
                return null;
            }
            double[] scores = new double[ps.size()];
            JsonNode arr = resp.get("scores");
            for (int i = 0; i < scores.length; i++) {
                scores[i] = arr.size() > i ? arr.get(i).asDouble() : 0.0;
            }
            return scores;
        } catch (Exception e) {
            log.warn("BGE-Reranker 不可用，降级为 RRF 排序: {}", e.getMessage());
            return null;
        }
    }
}
