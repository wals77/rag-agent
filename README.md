# 企业级可信 RAG 知识库问答系统（强制引用溯源）

带“强制引用溯源”的企业私有知识库问答系统：**每个回答中的每个事实都标注 `【来源：文件名，第X页】`**，未检索到可靠资料时拒绝作答，点击引用可直接跳转 PDF 对应页码。设计依据见 `doc/企业级可信RAG知识库问答系统`。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.4 / Spring AI 1.0 / JDK 17 |
| 前端 | Nuxt 3 + Vue 3 + TypeScript + TailwindCSS + pdf-vue3 |
| 本地大模型 | Ollama + Qwen2.5-7B（问答 / 语义分块 / HyDE） |
| 嵌入 | Ollama + nomic-embed-text（768 维，可换 bge-m3） |
| 检索 | Elasticsearch 8.13（单索引同时承载 向量 + BM25 + HyDE） |
| 元数据 | MySQL 8.0（documents / document_chunks / chunk_feedback / qa_logs） |
| 重排序 | BGE-Reranker（Python sidecar，sigmoid 归一化） |
| OCR（可选） | PaddleOCR Python sidecar（扫描件 PDF） |

## 系统流程

```
上传文档
  → 解析（PDFBox/POI/TXT；扫描页 → PaddleOCR 可选）
  → 规则粗切（父块 ~800 字，记录页码/章节/字符区间）
  → LLM 语义分块（Qwen 在父块内挑语义断点，校验文本无遗漏后采纳；父块记为 split）
  → 存 MySQL（含 parent_chunk_id / merged_into / edited_version_of 溯源链）
  → 嵌入并写入 ES（dense_vector + BM25 cjk 分析器）

提问
  → 三路召回：向量 knn + BM25 + HyDE（假设答案向量检索），RRF 合并去重
  → BGE-Reranker 精排取 Top 3
  → 强制引用 Prompt（Qwen2.5-7B）→ 引用标记校验（防幻觉页码/文件名）
  → 无资料/低于置信阈值/引用不可验证 → 拒绝作答
  → 写 qa_logs（含 cited_chunk_ids）
```

## 目录结构

```
.
├── backend/                     # Spring Boot 后端
│   ├── Dockerfile
│   └── src/main/resources/db/init.sql   # 与 docker-entrypoint-initdb.d 共用
├── frontend/                    # Nuxt3 前端（pages/chat、upload、chunk 管理、feedback、pdf-viewer）
├── services/
│   ├── reranker/                # BGE-Reranker FastAPI 服务（:8001）
│   └── ocr/                     # PaddleOCR FastAPI 服务（:8002，可选 profile）
├── docker-compose.yml
└── .env.example
```

## 快速开始（Docker Compose）

```bash
cp .env.example .env        # 可选；默认即可跑通

# 1. 构建并启动（首次会拉取 mysql/es/ollama 镜像）
docker compose up -d --build

# 2. 观察依赖就绪（首次会自动 pull qwen2.5:7b 与 nomic-embed-text，耗时较长）
docker compose logs -f ollama-init

# 3. 浏览器访问
open http://localhost:3000      # 前端
open http://localhost:8080/api/health   # 后端健康检查
```

> 若磁盘/内存受限，可先改 `.env` 使用小模型：`OLLAMA_CHAT_MODEL=qwen2.5:3b`，
> 或关闭 LLM 分块（`LLM_CHUNKING_ENABLED=false`，退化为纯规则分块）。

**需要 OCR 的扫描件 PDF（可选）**：

```bash
docker compose --profile ocr up -d
# 并把 OCR_BASE_URL 指向 http://ocr:8002（示例 compose 中默认空=关闭）
```

## 页面

| 路由 | 说明 |
| --- | --- |
| `/` | 问答主界面（ChatGPT 风格，回答内联可点击引用） |
| `/documents` | 文档列表（重解析 / 删除 / 自动刷新） |
| `/documents/upload` | 上传 PDF / DOCX / TXT（批量、进度） |
| `/documents/:id/chunks` | 单文档全部分块（编辑/合并/拆分/反馈） |
| `/admin/chunks` | 全部分块管理后台（筛选、勾选合并等） |
| `/admin/feedback` | 反馈中心（含修正文本新旧对照） |
| `/pdf-viewer` | 点击引用后打开的 PDF 预览（定位到页码） |

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/documents/upload` | 上传文档（multipart: file），触发异步流水线 |
| GET | `/api/documents` | 文档列表 |
| GET | `/api/documents/{docId}` | 文档详情 |
| POST | `/api/documents/{docId}/reprocess` | 重新解析 + 分块 |
| DELETE | `/api/documents/{docId}` | 删除文档（级联分块/向量/文件） |
| GET | `/api/documents/{docId}/chunks` | 某文档分块（后台） |
| GET | `/api/documents/{docId}/file` | 下载/预览原始文件（pdf-vue3 引用跳页） |
| POST | `/api/chat/ask` | **SSE 流式问答**（text/event-stream，强制引用） |
| GET | `/api/chunks` | 分块列表（docId/status/page/size 筛选） |
| PUT | `/api/chunks/{chunkId}` | 编辑分块（生成 edited 新版本，原块归档） |
| POST | `/api/chunks/merge` | 合并 ≥2 个 active 分块（同文档） |
| POST | `/api/chunks/split` | 拆分 1 个分块为多个子块 |
| POST | `/api/chunks/{chunkId}/feedback` | 提交分块反馈 |
| GET | `/api/chunks/{chunkId}/feedback` | 某分块的反馈记录 |
| GET | `/api/chunks/feedbacks` | 全量反馈（后台） |
| GET | `/api/health` | 健康检查（含 ES ping） |

### 问答接口（SSE）

`POST /api/chat/ask`，请求体 `{ "question": "...", "userId": "..." }`，响应为 `text/event-stream`，事件均为单行 JSON：

| 事件 | data 载荷 | 说明 |
| --- | --- | --- |
| `status` | `{"stage":"retrieve"}` / `{"stage":"generate","documents":[{docName,pageNum}]}` | 阶段提示 |
| `delta` | `{"text":"..."}` | 增量回答文本（可逐字渲染） |
| `done` | `AskResponse`（answer/citations/hasAnswer/retrievalScore/notice） | 结束载荷，以此为准渲染引用 |
| `error` | `{"message":"..."}` | 服务异常 |

```bash
curl -N -X POST http://localhost:8080/api/chat/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"违约金比例是多少？","userId":"user_001"}'
```

无资料 / 低于置信阈值 / 引用不可核验时不会流式输出，而是直接推送一条 `done`（`hasAnswer:false`）。

## 分块溯源语义

`document_chunks.status` 与溯源字段：

- `active`：参与检索的生效块（仅 active 会进入 ES）
- `split`：已被 LLM/人工拆分的父块，其子块通过 `parent_chunk_id` 指向它
- `merged`：被并入其它块的块，指向 `merged_into_chunk_id`
- `edited`：已被“编辑/修正”归档的旧版块；新版块用 `edited_version_of` 指回它

任一变更（编辑/合并/拆分/带修正文本的反馈）都会：写 MySQL → 更新 ES 向量索引（删除旧、写入新），保证检索结果即时一致。

## 引用强制机制

1. Prompt 硬性要求“每个事实紧跟 `【来源：文件名，第X页】`”，文件名/页码必须与参考列表完全一致。
2. 后端对回答做**引用校验**：每条标记必须在 Top-3 候选中找到对应 `docName + pageNum` 才放行。
3. 校验不通过会**重试一次**；仍不可验证 → 返回“未找到…未生成任何无依据内容”，`hasAnswer=false`。
4. 前端将标记渲染为引用胶囊并跳转 `/pdf-viewer?docId=..&page=..`。

## 主要配置（application.yml / 环境变量）

| 项 | 默认 | 说明 |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama 地址 |
| `OLLAMA_CHAT_MODEL` | `qwen2.5:7b` | 聊天/分块/HyDE 模型 |
| `OLLAMA_EMBED_MODEL` / `EMBEDDING_DIM` | `nomic-embed-text` / `768` | 嵌入模型；bge-m3→1024 |
| `RERANK_BASE_URL` | `http://localhost:8001` | BGE-Reranker；不可用自动降级 RRF |
| `OCR_BASE_URL` | 空 | PaddleOCR；空则扫描页标记为空 |
| `RULE_MAX_CHARS` | `800` | 规则父块最大字符数 |
| `LLM_CHUNKING_ENABLED` | `true` | LLM 语义分块开关 |
| `rag.retrieval.*` | — | 各路 topK / RRF / 置信阈值等 |

## 本地开发（不走 Docker）

```bash
# 需本地已装 MySQL / ES / Ollama（模型: ollama pull qwen2.5:7b && ollama pull nomic-embed-text）
cd backend && mvn spring-boot:run          # :8080
cd frontend && npm install && npm run dev  # :3000
```

## 常见问题

- **Q：文档上传后状态一直是 processing / failed？**
  看 `docker compose logs backend`。最常见是：Ollama 模型未拉取完、ES 未就绪、或扫描件 PDF 无文本且未启用 OCR。failed 的文档可在列表点“重处理”。
- **Q：reranker/OCR 模型很大？**
  首次调用自动从 HuggingFace 下载（bge-reranker-base 约 1.1GB），已挂 volume 持久化；OCR 服务默认不在 compose 中启动，仅 `--profile ocr` 时启用。
- **Q：换嵌入模型报维度错误？**
  向量维度在 ES mapping 中固定，换模型请先删索引（`docker compose down -v` 重建，或手动删除 rag_chunks 索引后重处理文档）。
