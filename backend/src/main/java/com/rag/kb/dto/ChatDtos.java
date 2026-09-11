package com.rag.kb.dto;

import java.util.List;

public class ChatDtos {

    /** conversationId 为空时表示无记忆的单轮问答（兼容旧调用） */
    public record AskRequest(String question, String userId, String conversationId) {}

    public record Citation(String chunkId, String docId, String docName, Integer pageNum,
                           String chapterTitle, String text) {}

    public record AskResponse(String answer, java.util.List<Citation> citations,
                              boolean hasAnswer, Double retrievalScore, String notice) {}

    public record ConversationDto(String conversationId, String userId, String title,
                                  java.time.LocalDateTime lastMessageTime,
                                  java.time.LocalDateTime createdAt) {}

    public record MessageDto(Integer messageId, String role, String content,
                             List<Citation> citations, java.time.LocalDateTime createdAt) {}

    public static AskResponse notFound(String question) {
        return new AskResponse(
            "抱歉，在已上传的文档中未找到关于“" + question + "”的相关记录，本次未生成任何无依据内容。",
            java.util.List.of(), false, null, "NO_DATA");
    }
}