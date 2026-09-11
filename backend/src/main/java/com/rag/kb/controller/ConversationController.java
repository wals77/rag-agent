package com.rag.kb.controller;

import com.rag.kb.chat.ConversationService;
import com.rag.kb.dto.ChatDtos;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public static class CreateRequest {
        private String userId;
        private String title;

        public String getUserId() { return userId; }
        public void setUserId(String v) { this.userId = v; }
        public String getTitle() { return title; }
        public void setTitle(String v) { this.title = v; }
    }

    @PostMapping
    public ChatDtos.ConversationDto create(@RequestBody CreateRequest request) {
        return conversationService.create(request.getUserId(), request.getTitle());
    }

    @GetMapping
    public List<ChatDtos.ConversationDto> list(@RequestParam(required = false) String userId) {
        return conversationService.list(userId);
    }

    @GetMapping("/{conversationId}/messages")
    public List<ChatDtos.MessageDto> messages(@PathVariable String conversationId) {
        return conversationService.messages(conversationId);
    }

    @DeleteMapping("/{conversationId}")
    public Map<String, Object> delete(@PathVariable String conversationId) {
        conversationService.delete(conversationId);
        return Map.of("deleted", true);
    }
}