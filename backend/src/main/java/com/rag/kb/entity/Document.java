package com.rag.kb.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @Column(name = "doc_id", length = 64)
    private String docId;

    @Column(name = "doc_name", nullable = false, length = 255)
    private String docName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "total_pages")
    private Integer totalPages = 0;

    @Column(name = "upload_time", insertable = false, updatable = false)
    private LocalDateTime uploadTime;

    @Column(name = "status", length = 20)
    private String status = "processing";

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getDocName() { return docName; }
    public void setDocName(String docName) { this.docName = docName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
    public LocalDateTime getUploadTime() { return uploadTime; }
    public void setUploadTime(LocalDateTime uploadTime) { this.uploadTime = uploadTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
