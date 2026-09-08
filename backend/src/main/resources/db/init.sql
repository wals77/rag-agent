-- 企业级可信RAG知识库问答系统 建表脚本（幂等，可重复执行）
-- 设计来源：doc/企业级可信RAG知识库问答系统
-- 注意：docker-compose 启动时同样会将该脚本挂载到 MySQL 容器的 docker-entrypoint-initdb.d 中执行

-- 1. 文档主表
CREATE TABLE IF NOT EXISTS documents (
    doc_id VARCHAR(64) PRIMARY KEY COMMENT '文档唯一ID',
    doc_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_path VARCHAR(500) COMMENT '存储路径',
    file_size BIGINT COMMENT '文件大小(字节)',
    total_pages INT DEFAULT 0 COMMENT '总页数',
    upload_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    status VARCHAR(20) DEFAULT 'processing' COMMENT 'processing/done/failed',
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档主表';

-- 2. 分块表（核心）
CREATE TABLE IF NOT EXISTS document_chunks (
    chunk_id VARCHAR(64) PRIMARY KEY COMMENT '分块唯一ID',
    doc_id VARCHAR(64) NOT NULL COMMENT '关联文档ID',
    doc_name VARCHAR(255) COMMENT '冗余文件名',
    page_num INT COMMENT '来源页码',
    chapter_title VARCHAR(255) COMMENT '章节标题/章节路径',
    heading_level INT NULL COMMENT 'Markdown标题层级(1-6)',
    chunk_text LONGTEXT NOT NULL COMMENT '分块纯文本',
    char_start INT COMMENT '原文起始字符位置',
    char_end INT COMMENT '原文结束字符位置',
    split_method VARCHAR(20) DEFAULT 'LLM' COMMENT 'LLM/RULE',
    llm_model VARCHAR(50) COMMENT '分块所用模型',
    status VARCHAR(20) DEFAULT 'active' COMMENT 'active/merged/split/edited',
    merged_into_chunk_id VARCHAR(64) NULL COMMENT '被合并到的目标块ID',
    parent_chunk_id VARCHAR(64) NULL COMMENT '父块ID(粗切块)',
    edited_version_of VARCHAR(64) NULL COMMENT '被编辑的原版块ID',
    quality_score FLOAT COMMENT '检索质量评分0-1',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id),
    INDEX idx_status (status),
    INDEX idx_parent (parent_chunk_id),
    CONSTRAINT fk_chunks_doc FOREIGN KEY (doc_id) REFERENCES documents(doc_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档分块表';

-- 3. 用户反馈表
CREATE TABLE IF NOT EXISTS chunk_feedback (
    feedback_id INT AUTO_INCREMENT PRIMARY KEY,
    chunk_id VARCHAR(64) NOT NULL COMMENT '关联分块ID',
    feedback_type VARCHAR(20) COMMENT 'bad_split/wrong_content/missing_context',
    user_comment TEXT COMMENT '用户备注',
    corrected_text LONGTEXT COMMENT '用户修正后的文本',
    old_chunk_text LONGTEXT COMMENT '修正前原文备份',
    user_id VARCHAR(50) COMMENT '反馈人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chunk_id (chunk_id),
    CONSTRAINT fk_feedback_chunk FOREIGN KEY (chunk_id) REFERENCES document_chunks(chunk_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分块反馈表';

-- 4. 问答日志（追溯引用）
CREATE TABLE IF NOT EXISTS qa_logs (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    question TEXT NOT NULL COMMENT '用户提问',
    answer LONGTEXT COMMENT '系统回答',
    cited_chunk_ids JSON COMMENT '引用的chunk_id列表',
    retrieval_score FLOAT COMMENT '检索相关性得分',
    response_time_ms INT COMMENT '响应耗时(毫秒)',
    user_id VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问答日志';
