package com.rag.kb.doc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 文档异步处理外壳：将耗时流水线放到独立线程池执行，失败时清理半成品并标记文档 failed。
 * 独立成一个 bean 是为了让 @Async 代理真正生效（避免 self-invocation 失效）。
 */
@Component
public class DocumentAsyncProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentAsyncProcessor.class);

    private final DocumentProcessingService processingService;

    public DocumentAsyncProcessor(DocumentProcessingService processingService) {
        this.processingService = processingService;
    }

    @Async("documentExecutor")
    public void run(String docId) {
        log.info("开始处理文档 {}", docId);
        try {
            processingService.process(docId);
            log.info("文档处理完成 {}", docId);
        } catch (Exception e) {
            log.error("文档处理失败 {}", docId, e);
            processingService.cleanupChunks(docId);
            processingService.markFailed(docId);
        }
    }
}
