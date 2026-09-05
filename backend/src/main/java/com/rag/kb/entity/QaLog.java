package com.rag.kb.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "qa_logs")
public class QaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", columnDefinition = "LONGTEXT")
    private String answer;

    @Column(name = "cited_chunk_ids", columnDefinition = "JSON")
    private String citedChunkIds;

    @Column(name = "retrieval_score")
    private Float retrievalScore;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Integer getLogId() { return logId; }
    public void setLogId(Integer v) { this.logId = v; }
    public String getQuestion() { return question; }
    public void setQuestion(String v) { this.question = v; }
    public String getAnswer() { return answer; }
    public void setAnswer(String v) { this.answer = v; }
    public String getCitedChunkIds() { return citedChunkIds; }
    public void setCitedChunkIds(String v) { this.citedChunkIds = v; }
    public Float getRetrievalScore() { return retrievalScore; }
    public void setRetrievalScore(Float v) { this.retrievalScore = v; }
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Integer v) { this.responseTimeMs = v; }
    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
