package com.rag.kb.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.rag.kb.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * PaddleOCR 识别服务客户端（可选）。服务不可用时返回 null，由上层降级。
 */
public class OcrClient {

    private static final Logger log = LoggerFactory.getLogger(OcrClient.class);

    private final RestClient rest;
    private final RagProperties props;

    public OcrClient(RestClient.Builder builder, RagProperties props) {
        this.props = props;
        this.rest = builder.build();
    }

    public boolean enabled() {
        String url = props.getOcr().getBaseUrl();
        return url != null && !url.isBlank();
    }

    public String recognize(String filename, int page, byte[] png) {
        if (!enabled()) return null;
        try {
            ByteArrayResource resource = new ByteArrayResource(png) {
                @Override
                public String getFilename() {
                    return "page.png";
                }
            };
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", resource);
            JsonNode body = rest.post()
                    .uri(props.getOcr().getBaseUrl() + "/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body != null && body.has("text")) {
                return body.get("text").asText();
            }
        } catch (Exception e) {
            log.warn("OCR 识别失败 doc={} page={}: {}", filename, page, e.getMessage());
        }
        return null;
    }
}
