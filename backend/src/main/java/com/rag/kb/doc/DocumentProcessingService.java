package com.rag.kb.doc;

import com.rag.kb.chunk.ChunkingEngine;
import com.rag.kb.chunk.RoughChunk;
import com.rag.kb.chunk.RuleSplitter;
import com.rag.kb.config.RagProperties;
import com.rag.kb.embedding.EmbeddingService;
import com.rag.kb.entity.Document;
import com.rag.kb.entity.DocumentChunk;
import com.rag.kb.es.EsIndexService;
import com.rag.kb.exception.BizException;
import com.rag.kb.parser.DocumentParser;
import com.rag.kb.parser.ParsedDocument;
import com.rag.kb.parser.ParserFactory;
import com.rag.kb.repository.DocumentChunkRepository;
import com.rag.kb.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档上传解析 + 智能分块 + 向量化入库 全流水线。
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final FileStorageService fileStorage;
    private final ParserFactory parserFactory;
    private final RuleSplitter ruleSplitter;
    private final ChunkingEngine chunkingEngine;
    private final EmbeddingService embeddingService;
    private final EsIndexService esIndexService;
    private final RagProperties props;

    public DocumentProcessingService(DocumentRepository documentRepository,
                                     DocumentChunkRepository chunkRepository,
                                     FileStorageService fileStorage,
                                     ParserFactory parserFactory,
                                     RuleSplitter ruleSplitter,
                                     ChunkingEngine chunkingEngine,
                                     EmbeddingService embeddingService,
                                     EsIndexService esIndexService,
                                     RagProperties props) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.fileStorage = fileStorage;
        this.parserFactory = parserFactory;
        this.ruleSplitter = ruleSplitter;
        this.chunkingEngine = chunkingEngine;
        this.embeddingService = embeddingService;
        this.esIndexService = esIndexService;
        this.props = props;
    }

    /**
     * 文档入库入口：校验扩展名、落盘、写入 documents(processing)，随后异步执行流水线。
     */
    public Document uploadCore(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new BizException("文件名不能为空");
        }
        DocumentParser parser = parserFactory.forFile(filename);
        if (parser == null) {
            throw new BizException("暂不支持该文件类型，仅支持 PDF / DOCX / TXT");
        }
        String docId = com.rag.kb.util.IdGen.docId(filename);
        String path = fileStorage.store(file, docId);

        Document doc = new Document();
        doc.setDocId(docId);
        doc.setDocName(filename);
        doc.setFilePath(path);
        doc.setFileSize(file.getSize());
        doc.setStatus("processing");
        documentRepository.save(doc);
        return doc;
    }

    /** 重新解析+分块（先清空旧分块与向量） */
    public void reprocessCore(String docId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new BizException("文档不存在: " + docId));
        cleanupChunks(docId);
        doc.setStatus("processing");
        documentRepository.save(doc);
    }

    public void markFailed(String docId) {
        documentRepository.findById(docId).ifPresent(d -> {
            d.setStatus("failed");
            documentRepository.save(d);
        });
    }

    public void process(String docId) throws IOException {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new BizException("文档不存在: " + docId));

        byte[] bytes = java.nio.file.Files.readAllBytes(fileStorage.load(doc.getFilePath()));
        DocumentParser parser = parserFactory.forFile(doc.getDocName());
        ParsedDocument parsed = parser.parse(doc.getDocName(), bytes);

        if (parsed.getPages().stream().allMatch(p -> p.getText() == null || p.getText().isBlank())) {
            throw new BizException("无法从文档中解析出文本内容（扫描件可能需要启用 OCR 服务后重新处理）");
        }

        List<RoughChunk> roughs = ruleSplitter.split(parsed);
        if (roughs.isEmpty()) {
            throw new BizException("文档内容为空，无法分块");
        }

        ChunkingEngine.ChunkBatch batch = chunkingEngine.build(doc, parsed, roughs);
        if (batch.chunks().isEmpty()) {
            throw new BizException("文档未生成任何分块");
        }

        // 1) 写入 MySQL（父块 + 子块元数据）
        chunkRepository.saveAll(batch.chunks());

        // 2) 向量化 + 写入 ES
        List<EsIndexService.IndexPayload> payloads = indexToEs(batch.actives());

        // 3) 更新文档状态
        doc.setTotalPages(parsed.totalPages());
        doc.setStatus("done");
        documentRepository.save(doc);
    }

    private List<EsIndexService.IndexPayload> indexToEs(List<DocumentChunk> actives) throws IOException {
        esIndexService.ensureIndex();
        List<EsIndexService.IndexPayload> payloads = new ArrayList<>();
        List<String> texts = actives.stream().map(DocumentChunk::getChunkText).toList();
        List<List<Float>> vectors = new ArrayList<>();
        int batchSize = 8;
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> sub = texts.subList(i, Math.min(i + batchSize, texts.size()));
            vectors.addAll(embeddingService.embedTexts(sub));
        }
        for (int i = 0; i < actives.size(); i++) {
            DocumentChunk c = actives.get(i);
            payloads.add(new EsIndexService.IndexPayload(
                    new EsIndexService.EsDoc(c.getChunkId(), c.getDocId(), c.getDocName(),
                            c.getPageNum(), c.getChapterTitle(), c.getChunkText()),
                    vectors.get(i)));
        }
        esIndexService.bulkIndexWithVectors(payloads);
        return payloads;
    }

    public void cleanupChunks(String docId) {
        List<DocumentChunk> chunks = chunkRepository.findByDocId(docId);
        if (!chunks.isEmpty()) {
            chunkRepository.deleteAllInBatch(chunks);
        }
        try {
            esIndexService.deleteByDocId(docId);
        } catch (Exception e) {
            log.warn("清理 ES 向量失败 doc={}: {}", docId, e.getMessage());
        }
    }
}
