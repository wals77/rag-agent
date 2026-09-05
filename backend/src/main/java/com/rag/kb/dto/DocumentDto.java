package com.rag.kb.dto;

import java.time.LocalDateTime;

public class DocumentDto {

    private String docId;
    private String docName;
    private String filePath;
    private Long fileSize;
    private Integer totalPages;
    private String status;
    private LocalDateTime uploadTime;

    public static DocumentDto from(com.rag.kb.entity.Document d) {
        DocumentDto o = new DocumentDto();
        o.docId = d.getDocId();
        o.docName = d.getDocName();
        o.filePath = d.getFilePath();
        o.fileSize = d.getFileSize();
        o.totalPages = d.getTotalPages();
        o.status = d.getStatus();
        o.uploadTime = d.getUploadTime();
        return o;
    }

    public String getDocId() { return docId; }
    public void setDocId(String v) { this.docId = v; }
    public String getDocName() { return docName; }
    public void setDocName(String v) { this.docName = v; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String v) { this.filePath = v; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long v) { this.fileSize = v; }
    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer v) { this.totalPages = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getUploadTime() { return uploadTime; }
    public void setUploadTime(LocalDateTime v) { this.uploadTime = v; }
}
