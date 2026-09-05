package com.rag.kb.dto;

import java.time.LocalDateTime;

public class FeedbackDto {

    private Integer feedbackId;
    private String chunkId;
    private String docId;
    private String docName;
    private Integer pageNum;
    private String feedbackType;
    private String userComment;
    private String correctedText;
    private String oldChunkText;
    private String userId;
    private LocalDateTime createdAt;

    public static FeedbackDto from(com.rag.kb.entity.ChunkFeedback f,
                                   com.rag.kb.entity.DocumentChunk chunk) {
        FeedbackDto o = new FeedbackDto();
        o.feedbackId = f.getFeedbackId();
        o.chunkId = f.getChunkId();
        o.feedbackType = f.getFeedbackType();
        o.userComment = f.getUserComment();
        o.correctedText = f.getCorrectedText();
        o.oldChunkText = f.getOldChunkText();
        o.userId = f.getUserId();
        o.createdAt = f.getCreatedAt();
        if (chunk != null) {
            o.docId = chunk.getDocId();
            o.docName = chunk.getDocName();
            o.pageNum = chunk.getPageNum();
        }
        return o;
    }

    public Integer getFeedbackId() { return feedbackId; }
    public void setFeedbackId(Integer v) { this.feedbackId = v; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String v) { this.chunkId = v; }
    public String getDocId() { return docId; }
    public void setDocId(String v) { this.docId = v; }
    public String getDocName() { return docName; }
    public void setDocName(String v) { this.docName = v; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer v) { this.pageNum = v; }
    public String getFeedbackType() { return feedbackType; }
    public void setFeedbackType(String v) { this.feedbackType = v; }
    public String getUserComment() { return userComment; }
    public void setUserComment(String v) { this.userComment = v; }
    public String getCorrectedText() { return correctedText; }
    public void setCorrectedText(String v) { this.correctedText = v; }
    public String getOldChunkText() { return oldChunkText; }
    public void setOldChunkText(String v) { this.oldChunkText = v; }
    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
