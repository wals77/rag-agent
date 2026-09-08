package com.rag.kb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务自定义配置（不依赖 Spring AI 内部的属性命名，便于在不同版本间保持稳定）。
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** 上传文件存储目录 */
    private String fileStoreDir = "./data/uploads";

    /** 索引名（同一索引内同时保存向量 + BM25 文本） */
    private String esIndexName = "rag_chunks";

    /** Ollama 地址（用于直接 HTTP 调用 embedding，避开不同 Spring AI 版本的 API 差异） */
    private String ollamaBaseUrl = "http://localhost:11434";

    /** 问答 / 语义分块 / HyDE 共用的大模型 */
    private String chatModel = "qwen2.5:7b-instruct";

    /** 嵌入模型 */
    private String embeddingModel = "nomic-embed-text";

    private Embedding embedding = new Embedding();
    private Chunking chunking = new Chunking();
    private Retrieval retrieval = new Retrieval();
    private Rerank rerank = new Rerank();
    private Ocr ocr = new Ocr();
    private Es es = new Es();
    private Cors cors = new Cors();

    public static class Embedding {
        /** 向量维度，nomic-embed-text=768, bge-m3=1024 */
        private int dim = 768;
        public int getDim() { return dim; }
        public void setDim(int dim) { this.dim = dim; }
    }

    public static class Chunking {
        /** 规则粗切（父块）单块最大字符数 */
        private int ruleMaxChars = 800;
        /** 是否启用 LLM 语义分块（父块内部再切） */
        private boolean llmEnabled = true;
        /** 单个父块最多切出多少子块 */
        private int llmMaxPieces = 6;
        /** 语义分块输出失败时回退纯规则 */
        private boolean llmRequired = false;
        public int getRuleMaxChars() { return ruleMaxChars; }
        public void setRuleMaxChars(int v) { this.ruleMaxChars = v; }
        public boolean isLlmEnabled() { return llmEnabled; }
        public void setLlmEnabled(boolean v) { this.llmEnabled = v; }
        public int getLlmMaxPieces() { return llmMaxPieces; }
        public void setLlmMaxPieces(int v) { this.llmMaxPieces = v; }
        public boolean isLlmRequired() { return llmRequired; }
        public void setLlmRequired(boolean v) { this.llmRequired = v; }
    }

    public static class Retrieval {
        private int vectorTopK = 3;
        private int bm25TopK = 10;
        private int hydeTopK = 10;
        private int rrfK = 60;
        private int topN = 3;
        private int rerankCandidates = 12;
        /** 检索不到足够资料时的置信阈值（reranker 归一化分数） */
        private double confidenceThreshold = 0.10;
        public int getVectorTopK() { return vectorTopK; }
        public void setVectorTopK(int v) { this.vectorTopK = v; }
        public int getBm25TopK() { return bm25TopK; }
        public void setBm25TopK(int v) { this.bm25TopK = v; }
        public int getHydeTopK() { return hydeTopK; }
        public void setHydeTopK(int v) { this.hydeTopK = v; }
        public int getRrfK() { return rrfK; }
        public void setRrfK(int v) { this.rrfK = v; }
        public int getTopN() { return topN; }
        public void setTopN(int v) { this.topN = v; }
        public int getRerankCandidates() { return rerankCandidates; }
        public void setRerankCandidates(int v) { this.rerankCandidates = v; }
        public double getConfidenceThreshold() { return confidenceThreshold; }
        public void setConfidenceThreshold(double v) { this.confidenceThreshold = v; }
    }

    public static class Rerank {
        /** BGE-Reranker Python 服务地址，为空则降级为 RRF 排序 */
        private String baseUrl = "http://localhost:8001";
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
    }

    public static class Ocr {
        /** PaddleOCR Python 服务地址，为空则扫描件 PDF 页将被标记为不可解析 */
        private String baseUrl = "";
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
    }

    public static class Es {
        private String uris = "http://localhost:9200";
        private String username = "";
        private String password = "";
        public String getUris() { return uris; }
        public void setUris(String v) { this.uris = v; }
        public String getUsername() { return username; }
        public void setUsername(String v) { this.username = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { this.password = v; }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> v) { this.allowedOrigins = v; }
    }

    public String getFileStoreDir() { return fileStoreDir; }
    public void setFileStoreDir(String v) { this.fileStoreDir = v; }
    public String getEsIndexName() { return esIndexName; }
    public void setEsIndexName(String v) { this.esIndexName = v; }
    public String getOllamaBaseUrl() { return ollamaBaseUrl; }
    public void setOllamaBaseUrl(String v) { this.ollamaBaseUrl = v; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String v) { this.chatModel = v; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String v) { this.embeddingModel = v; }
    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding v) { this.embedding = v; }
    public Chunking getChunking() { return chunking; }
    public void setChunking(Chunking v) { this.chunking = v; }
    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval v) { this.retrieval = v; }
    public Rerank getRerank() { return rerank; }
    public void setRerank(Rerank v) { this.rerank = v; }
    public Ocr getOcr() { return ocr; }
    public void setOcr(Ocr v) { this.ocr = v; }
    public Es getEs() { return es; }
    public void setEs(Es v) { this.es = v; }
    public Cors getCors() { return cors; }
    public void setCors(Cors v) { this.cors = v; }
}
