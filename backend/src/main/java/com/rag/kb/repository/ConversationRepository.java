package com.rag.kb.repository;

import com.rag.kb.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByUserIdOrderByLastMessageTimeDesc(String userId);
}