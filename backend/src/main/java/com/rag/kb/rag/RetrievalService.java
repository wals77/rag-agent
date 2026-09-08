package com.rag.kb.rag;

import com.rag.kb.config.RagProperties;
import com.rag.kb.embedding.EmbeddingService;
import com.rag.kb.entity.DocumentChunk;
import com.rag.kb.es.EsIndexService;
import com.rag.kb.repository.DocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多路召回：向量检索 + BM25 + HyDE，ES 统一实现；RRF 合并去重；
 * 之后交 BGE-Reranker 精排，取 Top N 用于强制引用问答。
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    public record Candidate(DocumentChunk chunk, double score, List<String> sources) {}

    private final EsIndexService esIndexService;
    private final EmbeddingService embeddingService;
    private final ChatModel chatModel;
    private final RerankClient rerankClient;
    private final DocumentChunkRepository chunkRepository;
    private final RagProperties props;

    public RetrievalService(EsIndexService esIndexService,
                            EmbeddingService embeddingService,
                            ChatModel chatModel,
                            RerankClient rerankClient,
                            DocumentChunkRepository chunkRepository,
                            RagProperties props) {
        this.esIndexService = esIndexService;
        this.embeddingService = embeddingService;
        this.chatModel = chatModel;
        this.rerankClient = rerankClient;
        this.chunkRepository = chunkRepository;
        this.props = props;
    }

    /**
     * 检索并精排。@return 按相关度降序的候选（已截断为 topN）
     */
    public List<Candidate> retrieve(String question) {
        RagProperties.Retrieval cfg = props.getRetrieval();
        List<EsIndexService.EsHit> bm25Hits = safeBm25(question, cfg.getBm25TopK());
        List<EsIndexService.EsHit> vectorHits = safeVectorSearch(question, cfg.getVectorTopK());
        //List<EsIndexService.EsHit> hydeHits = safeHyde(question, cfg.getHydeTopK());

        List<List<EsIndexService.EsHit>> lists = new ArrayList<>();
        lists.add(bm25Hits);
        //lists.add(vectorHits);
//        if (!hydeHits.isEmpty()) {
//            lists.add(hydeHits);
//        }

        // RRF 合并
        Map<String, Double> fused = new LinkedHashMap<>();
        for (List<EsIndexService.EsHit> hits : lists) {
            if (hits == null) {
                continue;
            }
            for (int i = 0; i < hits.size(); i++) {
                String id = hits.get(i).doc().chunkId();
                fused.merge(id, 1.0 / (cfg.getRrfK() + i + 1), Double::sum);
            }
        }
        if (fused.isEmpty()) {
            return List.of();
        }

        List<String> ids = fused.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(cfg.getRerankCandidates())
                .map(Map.Entry::getKey)
                .toList();

        Map<String, DocumentChunk> chunkById = loadChunks(ids);
        List<DocumentChunk> candidates = ids.stream()
                .map(chunkById::get)
                .filter(c -> c != null)
                .toList();

        // BGE-Reranker 精排
        List<Candidate> ranked = rerank(question, candidates, cfg.getTopN());
        return ranked;
    }

    /** 置信度：有 reranker 时为 top1 分数；否则用 rrf 归一值近似，并标记 uncertain */
    public record Confidence(double value, boolean fromRerank) {}

    public Confidence confidenceOf(List<Candidate> ranked) {
        if (ranked.isEmpty()) {
            return new Confidence(0, true);
        }
        double v = ranked.get(0).score();
        boolean fromRerank = ranked.stream().anyMatch(c -> c.sources().contains("rerank"));
        return new Confidence(Math.max(0, Math.min(1, v)), fromRerank);
    }

    private List<Candidate> rerank(String question, List<DocumentChunk> candidates, int topN) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<RerankClient.Passage> passages = candidates.stream()
                .map(c -> new RerankClient.Passage(c.getChunkId(),
                        truncate(c.getChunkText(), 600), c.getDocName(), c.getPageNum()))
                .toList();
        double[] scores = rerankClient.rerank(question, passages);
        List<Candidate> out = new ArrayList<>();
        if (scores != null) {
            for (int i = 0; i < candidates.size(); i++) {
                out.add(new Candidate(candidates.get(i), scores[i], List.of("rerank")));
            }
            out.sort(Comparator.comparingDouble((Candidate c) -> c.score()).reversed());
        } else {
            // 降级：按候选顺序赋线性置信度
            for (int i = 0; i < candidates.size(); i++) {
                double sc = candidates.size() == 1 ? 0.9 : 0.9 - i * 0.25;
                out.add(new Candidate(candidates.get(i), Math.max(0.05, sc), List.of("rrf")));
            }
        }
        return out.stream().limit(Math.max(1, topN)).toList();
    }

    private Map<String, DocumentChunk> loadChunks(List<String> ids) {
        List<DocumentChunk> rows = chunkRepository.findByChunkIdInAndStatus(ids, DocumentChunk.STATUS_ACTIVE);
        Map<String, DocumentChunk> map = new HashMap<>();
        for (DocumentChunk r : rows) {
            map.put(r.getChunkId(), r);
        }
        return map;
    }

    private List<EsIndexService.EsHit> safeVectorSearch(String question, int topK) {
        try {
            List<Float> qv = embeddingService.embed(question);
            return esIndexService.knnSearch(qv, topK);
        } catch (Exception e) {
            log.warn("向量召回失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<EsIndexService.EsHit> safeBm25(String question, int topK) {
        try {
            return esIndexService.bm25Search(question, topK);
        } catch (Exception e) {
            log.warn("BM25 召回失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<EsIndexService.EsHit> safeHyde(String question, int topK) {
        try {
            String hypothetical = hydeHypothesis(question);
            if (hypothetical == null || hypothetical.isBlank()) {
                return List.of();
            }
            List<Float> qv = embeddingService.embed(hypothetical);
            return esIndexService.knnSearch(qv, topK);
        } catch (Exception e) {
            log.warn("HyDE 召回失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 生成假设性答案文档（HyDE），失败返回 null */
    private String hydeHypothesis(String question) {
        String sys = "你是一个严谨的资料检索助手。请用陈述句写一小段(80~200字)假设性文档，内容应是文档库中可能回答该问题的原文表述，不要出现“根据”“可能”“无法确定”等检索性措辞。";
        try {
            ChatResponse resp = chatModel.call(new Prompt(
                    List.of(new SystemMessage(sys), new UserMessage("问题：" + question))));
            return resp.getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("HyDE 假设生成失败: {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
