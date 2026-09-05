package com.rag.kb.dto;

public class ChatDtos {

    public record AskRequest(String question, String userId) {}

    public record Citation(String chunkId, String docId, String docName, Integer pageNum,
                           String chapterTitle, String text) {}

    public record AskResponse(String answer, java.util.List<Citation> citations,
                              boolean hasAnswer, Double retrievalScore, String notice) {}

    public static AskResponse notFound(String question) {
        return new AskResponse(
            "抱歉，在已上传的文档中未找到关于“" + question + "”的相关记录，本次未生成任何无依据内容。",
            java.util.List.of(), false, null, "NO_DATA");
    }
}
