package com.rag.kb.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * BGE-Reranker 重排序客户端。指向 reranker Python 服务，不可用时返回 null（调用方降级为 RRF 顺序）。
 */
@Service
public class RerankClient {

    private static final Logger log = LoggerFactory.getLogger(RerankClient.class);

    public record Passage(String id, String text, String docName, Integer pageNum) {}

    private final RestTemplate rest;
    private final RagProperties props;

    public RerankClient(RagProperties props, ObjectMapper mapper) {
        this.props = props;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.rest = new RestTemplate(factory);
        this.rest.getMessageConverters().add(new MappingJackson2HttpMessageConverter(mapper));
    }

    public boolean available() {
        String url = props.getRerank().getBaseUrl();
        return url != null && !url.isBlank();
    }

    /**
     * 调用 BGE-Reranker /rerank 接口：
     * 请求 {"query":"...","documents":["文本1","文本2",...]}，
     * 响应 {"results":[{"index":2,"document":"...","score":3.1},...]}（按 score 降序，index 为入参下标）。
     *
     * @return 与 passages 等序的归一化相关度分数(0~1，sigmoid)；服务不可用/响应异常返回 null（调用方降级为 RRF 顺序）
     */
    public double[] rerank(String query, List<Passage> passages) {
        if (!available() || passages.isEmpty()) return null;
        try {
            List<String> documents = passages.stream().map(Passage::text).toList();
            Map<String, Object> body = Map.of("query", query, "documents", documents);
            JsonNode resp = rest.postForObject(
                    props.getRerank().getBaseUrl() + "/rerank",
                    body,
                    JsonNode.class);
            JsonNode results = resp == null ? null : resp.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                log.warn("BGE-Reranker 响应缺少 results，降级为 RRF 排序");
                return null;
            }
            double[] scores = new double[passages.size()];
            java.util.Arrays.fill(scores, Double.NaN);
            for (JsonNode r : results) {
                if (!r.has("index") || !r.has("score")) continue;
                int idx = r.get("index").asInt(-1);
                if (idx < 0 || idx >= scores.length) continue;
                scores[idx] = sigmoid(r.get("score").asDouble());
            }
            for (int i = 0; i < scores.length; i++) {
                if (Double.isNaN(scores[i])) scores[i] = 0.0;
            }
            return scores;
        } catch (Exception e) {
            log.warn("BGE-Reranker 不可用，降级为 RRF 排序: {}", e.getMessage());
            return null;
        }
    }

    /** rerank 原始分（logit，无界） -> 0~1 归一化，保持单调性 */
    private static double sigmoid(double x) {
        if (x > 30) return 1.0;
        if (x < -30) return 0.0;
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
