package com.rag.kb.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Ollama embedding 调用（HTTP 直连 /api/embed），
 * 用于分块向量化、查询向量化与 HyDE 假设文档向量化。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final RagProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public EmbeddingService(RagProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<Float> embed(String text) {
        List<List<Float>> all = embedTexts(List.of(text));
        return all.isEmpty() ? List.of() : all.get(0);
    }

    /** 批量嵌入，返回与输入等序的向量列表 */
    public List<List<Float>> embedTexts(List<String> texts) {
        List<List<Float>> result = new ArrayList<>();
        if (texts.isEmpty()) return result;
        try {
            String payload = mapper.writeValueAsString(
                    java.util.Map.of("model", props.getEmbeddingModel(), "input", texts));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(props.getOllamaBaseUrl() + "/api/embed"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("ollama embed http " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode arr = root.path("embeddings");
            for (JsonNode vec : arr) {
                List<Float> floats = new ArrayList<>();
                for (JsonNode v : vec) floats.add((float) v.asDouble());
                result.add(floats);
            }
        } catch (Exception e) {
            throw new IllegalStateException("嵌入向量生成失败(检查 Ollama 及模型 " + props.getEmbeddingModel() + "): " + e.getMessage(), e);
        }
        return result;
    }
}
