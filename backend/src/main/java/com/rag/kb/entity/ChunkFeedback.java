package com.rag.kb.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chunk_feedback")
public class ChunkFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Integer feedbackId;

    @Column(name = "chunk_id", nullable = false, length = 64)
    private String chunkId;

    @Column(name = "feedback_type", length = 20)
    private String feedbackType;

    @Column(name = "user_comment", columnDefinition = "TEXT")
    private String userComment;

    @Column(name = "corrected_text", columnDefinition = "LONGTEXT")
    private String correctedText;

    @Column(name = "old_chunk_text", columnDefinition = "LONGTEXT")
    private String oldChunkText;

    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Integer getFeedbackId() { return feedbackId; }
    public void setFeedbackId(Integer v) { this.feedbackId = v; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String v) { this.chunkId = v; }
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
