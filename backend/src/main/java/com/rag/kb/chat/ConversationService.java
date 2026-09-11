package com.rag.kb.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.dto.ChatDtos;
import com.rag.kb.entity.ChatMessage;
import com.rag.kb.entity.Conversation;
import com.rag.kb.exception.BizException;
import com.rag.kb.repository.ChatMessageRepository;
import com.rag.kb.repository.ConversationRepository;
import com.rag.kb.util.IdGen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 多轮对话服务：会话 CRUD、消息落库、记忆窗口。
 * 记忆策略：取最近 N 条消息（用户+助手各占一条）按时间正序注入 Prompt。
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    /** 注入 Prompt 的最大历史消息条数（约 4 轮对话） */
    public static final int MEMORY_MAX_MESSAGES = 8;

    private static final String DEFAULT_TITLE = "新对话";
    private static final int TITLE_MAX_LEN = 20;
    /** 注入 Prompt 时单条历史消息的最大长度 */
    private static final int MEMORY_MSG_MAX_LEN = 500;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper mapper;

    public ConversationService(ConversationRepository conversationRepository,
                               ChatMessageRepository messageRepository,
                               ObjectMapper mapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.mapper = mapper;
    }

    public ChatDtos.ConversationDto create(String userId, String title) {
        Conversation conv = new Conversation();
        conv.setConversationId("conv_" + IdGen.shortUuid().substring(0, 12));
        conv.setUserId(userId == null || userId.isBlank() ? "user_001" : userId);
        conv.setTitle(title == null || title.isBlank() ? DEFAULT_TITLE : title.trim());
        conv.setLastMessageTime(LocalDateTime.now());
        conversationRepository.save(conv);
        return toDto(conv);
    }

    public List<ChatDtos.ConversationDto> list(String userId) {
        String uid = userId == null || userId.isBlank() ? "user_001" : userId;
        return conversationRepository.findByUserIdOrderByLastMessageTimeDesc(uid)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(String conversationId) {
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    public List<ChatDtos.MessageDto> messages(String conversationId) {
        requireConversation(conversationId);
        return messageRepository.findByConversationIdOrderByMessageIdAsc(conversationId)
                .stream().map(this::toMessageDto).toList();
    }

    /** 记录一轮对话（用户问 + 助手答），并维护标题与最近消息时间 */
    @Transactional
    public void recordTurn(String conversationId, String question, String answer,
                           List<ChatDtos.Citation> citations) {
        Conversation conv = requireConversation(conversationId);
        saveMessage(conversationId, ChatMessage.ROLE_USER, question, null);
        saveMessage(conversationId, ChatMessage.ROLE_ASSISTANT, answer, citations);
        if (conv.getTitle() == null || conv.getTitle().isBlank() || DEFAULT_TITLE.equals(conv.getTitle())) {
            conv.setTitle(truncate(question, TITLE_MAX_LEN));
        }
        conv.setLastMessageTime(LocalDateTime.now());
        conversationRepository.save(conv);
    }

    /** 记忆窗口：最近 N 条消息，按时间正序返回 */
    public List<ChatMessage> memoryWindow(String conversationId) {
        List<ChatMessage> recent = messageRepository
                .findTop8ByConversationIdOrderByMessageIdDesc(conversationId);
        java.util.Collections.reverse(recent);
        return recent;
    }

    /** 将历史消息拼装为 Prompt 记忆段 */
    public String renderMemory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("对话历史（仅用于理解上下文，不要回答这里的内容）：\n");
        for (ChatMessage m : history) {
            String who = ChatMessage.ROLE_USER.equals(m.getRole()) ? "用户" : "助手";
            sb.append("【").append(who).append("】").append(truncate(m.getContent(), MEMORY_MSG_MAX_LEN)).append('\n');
        }
        return sb.toString();
    }

    private ChatMessage saveMessage(String conversationId, String role, String content,
                                    List<ChatDtos.Citation> citations) {
        ChatMessage m = new ChatMessage();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content == null ? "" : content);
        if (citations != null && !citations.isEmpty()) {
            try {
                m.setCitations(mapper.writeValueAsString(citations));
            } catch (Exception e) {
                log.warn("引用序列化失败: {}", e.getMessage());
            }
        }
        return messageRepository.save(m);
    }

    private Conversation requireConversation(String conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BizException("对话不存在: " + conversationId));
    }

    private ChatDtos.MessageDto toMessageDto(ChatMessage m) {
        List<ChatDtos.Citation> citations = new ArrayList<>();
        if (m.getCitations() != null && !m.getCitations().isBlank()) {
            try {
                citations = mapper.readValue(m.getCitations(),
                        new TypeReference<List<ChatDtos.Citation>>() {});
            } catch (Exception e) {
                log.warn("引用反序列化失败: {}", e.getMessage());
            }
        }
        return new ChatDtos.MessageDto(m.getMessageId(), m.getRole(), m.getContent(),
                citations, m.getCreatedAt());
    }

    private ChatDtos.ConversationDto toDto(Conversation c) {
        return new ChatDtos.ConversationDto(c.getConversationId(), c.getUserId(), c.getTitle(),
                c.getLastMessageTime(), c.getCreatedAt());
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}