package com.rag.kb.dto;

import java.util.List;

public class ChunkDtos {

    public record ChunkUpdateRequest(String chunkText) {}

    public record MergeRequest(List<String> chunkIds, String mergedText) {}

    public record SplitRequest(String chunkId, List<String> pieces) {}

    public record FeedbackRequest(String feedbackType, String userComment,
                                  String correctedText, String userId) {}

    public record FeedbackResult(Integer feedbackId, String status, String notice, ChunkDto newChunk) {}

    public record ChunkMutationResult(ChunkDto chunk, List<ChunkDto> replacedChunks, String notice) {}
}
