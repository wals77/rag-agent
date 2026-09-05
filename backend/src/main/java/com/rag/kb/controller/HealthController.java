package com.rag.kb.controller;

import com.rag.kb.es.EsIndexService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final EsIndexService esIndexService;

    public HealthController(EsIndexService esIndexService) {
        this.esIndexService = esIndexService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "ok");
        try {
            m.put("elasticsearch", esIndexService.ping());
        } catch (Exception e) {
            m.put("elasticsearch", false);
        }
        return m;
    }
}
