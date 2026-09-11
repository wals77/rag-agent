package com.rag.kb.repository;

import com.rag.kb.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {

    List<ChatMessage> findByConversationIdOrderByMessageIdAsc(String conversationId);

    List<ChatMessage> findTop8ByConversationIdOrderByMessageIdDesc(String conversationId);

    @Modifying
    void deleteByConversationId(String conversationId);
}