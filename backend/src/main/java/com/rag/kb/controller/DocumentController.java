package com.rag.kb.controller;

import com.rag.kb.doc.DocumentAsyncProcessor;
import com.rag.kb.doc.DocumentProcessingService;
import com.rag.kb.doc.FileStorageService;
import com.rag.kb.dto.ChunkDto;
import com.rag.kb.dto.DocumentDto;
import com.rag.kb.dto.PageResult;
import com.rag.kb.entity.Document;
import com.rag.kb.exception.BizException;
import com.rag.kb.repository.DocumentRepository;
import com.rag.kb.chunk.ChunkManagementService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentProcessingService processingService;
    private final DocumentAsyncProcessor asyncProcessor;
    private final FileStorageService fileStorage;
    private final ChunkManagementService chunkManagementService;

    public DocumentController(DocumentRepository documentRepository,
                              DocumentProcessingService processingService,
                              DocumentAsyncProcessor asyncProcessor,
                              FileStorageService fileStorage,
                              ChunkManagementService chunkManagementService) {
        this.documentRepository = documentRepository;
        this.processingService = processingService;
        this.asyncProcessor = asyncProcessor;
        this.fileStorage = fileStorage;
        this.chunkManagementService = chunkManagementService;
    }

    @PostMapping("/upload")
    public DocumentDto upload(@RequestParam("file") MultipartFile file) throws IOException {
        Document doc = processingService.uploadCore(file);
        asyncProcessor.run(doc.getDocId());
        return DocumentDto.from(doc);
    }

    @GetMapping
    public List<DocumentDto> list(@RequestParam(required = false) String status) {
        var docs = documentRepository.findAll(Sort.by(Sort.Direction.DESC, "uploadTime"));
        if (status != null && !status.isBlank()) {
            docs = docs.stream().filter(d -> status.equals(d.getStatus())).toList();
        }
        return docs.stream().map(DocumentDto::from).toList();
    }

    @GetMapping("/{docId}")
    public DocumentDto get(@PathVariable String docId) {
        return DocumentDto.from(requireDoc(docId));
    }

    @PostMapping("/{docId}/reprocess")
    public DocumentDto reprocess(@PathVariable String docId) {
        processingService.reprocessCore(docId);
        asyncProcessor.run(docId);
        return DocumentDto.from(requireDoc(docId));
    }

    @DeleteMapping("/{docId}")
    public void delete(@PathVariable String docId) {
        Document doc = requireDoc(docId);
        processingService.cleanupChunks(docId);
        fileStorage.delete(doc.getFilePath());
        documentRepository.delete(doc);
    }

    @GetMapping("/{docId}/chunks")
    public PageResult<ChunkDto> chunks(@PathVariable String docId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size,
                                       @RequestParam(required = false) String status) {
        return chunkManagementService.listChunks(docId, status, page, size);
    }

    /** 下载原始文件（pdf-vue3 预览/引用跳页） */
    @GetMapping("/{docId}/file")
    public ResponseEntity<Resource> file(@PathVariable String docId) throws IOException {
        Document doc = requireDoc(docId);
        java.nio.file.Path p = fileStorage.load(doc.getFilePath());
        Resource res = new UrlResource(p.toUri());
        if (!res.exists()) {
            throw new BizException("文件已丢失");
        }
        String encoded = URLEncoder.encode(doc.getDocName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileStorage.contentType(doc.getDocName())))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encoded)
                .body(res);
    }

    private Document requireDoc(String docId) {
        return documentRepository.findById(docId)
                .orElseThrow(() -> new BizException("文档不存在: " + docId));
    }
}
