package com.rag.kb.controller;

import com.rag.kb.chat.ChatService;
import com.rag.kb.dto.ChatDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * SSE 流式问答。响应为 text/event-stream：
     * status / delta / done / error 四种事件，done 携带最终 AskResponse。
     */
    @PostMapping(value = "/ask", produces = "text/event-stream")
    public SseEmitter ask(@Valid @RequestBody ChatDtos.AskRequest request) {
        return chatService.askSse(request);
    }
}
