package com.rag.kb.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.RagProperties;
import com.rag.kb.dto.ChatDtos;
import com.rag.kb.entity.QaLog;
import com.rag.kb.exception.BizException;
import com.rag.kb.rag.RetrievalService;
import com.rag.kb.repository.QaLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 问答服务（SSE 流式）：多路召回 -> TopN 精排 -> 强制引用 Prompt -> Qwen 流式生成 ->
 * 引用校验/结构化 -> 写问答日志。
 *
 * SSE 事件（均为 JSON，走 data: 行）：
 *  - status: {"stage":"retrieve"|"generate"}      阶段提示（可选）
 *  - delta : {"text":"..."}                        增量回答文本
 *  - done  : 完整 AskResponse                      结束载荷（以此为准渲染）
 *  - error : {"message":"..."}                     出错
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String NOT_FOUND_MARK = "未找到";
    /** 从提交任务到真正开流前的短暂等待，确保 MVC 异步上下文就绪 */
    private static final long EMITTER_GRACE_MS = 200L;

    private final ChatModel chatModel;
    private final RetrievalService retrievalService;
    private final QaLogRepository qaLogRepository;
    private final ObjectMapper mapper;
    private final RagProperties props;
    private final Executor chatExecutor;

    public ChatService(ChatModel chatModel, RetrievalService retrievalService,
                       QaLogRepository qaLogRepository, ObjectMapper mapper,
                       RagProperties props, Executor chatExecutor) {
        this.chatModel = chatModel;
        this.retrievalService = retrievalService;
        this.qaLogRepository = qaLogRepository;
        this.mapper = mapper;
        this.props = props;
        this.chatExecutor = chatExecutor;
    }

    /** SSE 接口入口：立即返回 SseEmitter，后续在后台线程推流。 */
    public SseEmitter askSse(ChatDtos.AskRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onTimeout(() -> log.warn("SSE 连接超时，question={}", request.question()));
        emitter.onError(e -> log.warn("SSE 连接异常: {}", e.getMessage()));

        chatExecutor.execute(() -> runSse(request, emitter));
        return emitter;
    }

    private void runSse(ChatDtos.AskRequest request, SseEmitter emitter) {
        long start = System.currentTimeMillis();
        boolean[] cancelled = { false };
        ChatDtos.AskResponse result = null;
        try {
            Thread.sleep(EMITTER_GRACE_MS);
            String question = request.question() == null ? "" : request.question().trim();
            if (question.isEmpty()) {
                throw new BizException("问题不能为空");
            }

            emit(emitter, cancelled, "status", Map.of("stage", "retrieve"));
            List<RetrievalService.Candidate> ranked = retrievalService.retrieve(question);
            RetrievalService.Confidence confidence = retrievalService.confidenceOf(ranked);

            if (ranked.isEmpty() ||
                    (confidence.fromRerank() && confidence.value() < props.getRetrieval().getConfidenceThreshold())) {
                result = ChatDtos.notFound(question);
                emit(emitter, cancelled, "done", result);
                return;
            }

            emit(emitter, cancelled, "status", Map.of(
                    "stage", "generate",
                    "documents", ranked.stream()
                            .map(c -> Map.of("docName", c.chunk().getDocName(), "pageNum", c.chunk().getPageNum()))
                            .distinct()
                            .toList()));

            String full = streamGenerate(question, ranked, emitter, cancelled);
            if (cancelled[0]) return;

            result = buildResponse(question, ranked, full);
            emit(emitter, cancelled, "done", result);
        } catch (BizException e) {
            result = ChatDtos.notFound(request.question());
            safeEmit(emitter, "done", result);
        } catch (Exception e) {
            log.error("SSE 问答失败", e);
            safeEmit(emitter, "error", Map.of("message", "服务异常: " + e.getMessage()));
        } finally {
            if (result != null) {
                saveLog(request, result, null, System.currentTimeMillis() - start);
            }
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // already closed
            }
        }
    }

    /** 调用 Qwen 流式生成，把增量文本逐段通过 SSE 推送，并返回完整文本。 */
    private String streamGenerate(String question, List<RetrievalService.Candidate> ranked,
                                  SseEmitter emitter, boolean[] cancelled) {
        String system = """
                你是一个企业私域知识库的“引用可追溯”问答助手。请严格依据下方的【参考资料】回答用户问题。

                硬性要求：
                1. 回答中的每一个事实、数字或结论，都必须紧跟引用标记，格式为：【来源：文件名，第X页】
                2. 引用中的文件名、页码必须与参考资料中的完全一致，严禁编造或改写文件名/页码。
                3. 如果参考资料中没有用户问题的相关信息，请只回答：抱歉，在已上传的文档中未找到关于“XXX”的相关记录，本次未生成任何无依据内容。严禁编造或使用外部知识。
                4. 如果参考资料之间存在矛盾，请把矛盾各方的表述连同各自出处全部列出，让用户自行判断，不要自行融合成一个答案。
                5. 只能使用参考资料中的内容，不要引用外部知识，不要自行补充任何资料中没有的企业、机构、数据或专有名词。
                """;

        StringBuilder refs = new StringBuilder("参考资料：\n");
        int i = 1;
        for (RetrievalService.Candidate c : ranked) {
            refs.append("【").append(i).append("】文件名：").append(c.chunk().getDocName())
                    .append("，第").append(c.chunk().getPageNum()).append("页：")
                    .append(truncate(c.chunk().getChunkText(), 900)).append("\n");
            i++;
        }
        refs.append("\n用户问题：").append(question);

        OllamaOptions options = OllamaOptions.builder()
                .model(props.getChatModel())
                .temperature(0.2)
                .build();
        Prompt prompt = new Prompt(List.of(new SystemMessage(system), new UserMessage(refs.toString())), options);

        StringBuilder full = new StringBuilder();
        for (ChatResponse chunk : chatModel.stream(prompt).toIterable()) {
            if (chunk.getResult() == null || chunk.getResult().getOutput() == null) continue;
            String token = chunk.getResult().getOutput().getText();
            if (token == null || token.isEmpty()) continue;
            full.append(token);
            if (!emit(emitter, cancelled, "delta", Map.of("text", token))) {
                break;
            }
        }
        return full.toString();
    }

    private ChatDtos.AskResponse buildResponse(String question, List<RetrievalService.Candidate> ranked,
                                               String answer) {
        if (answer == null) answer = "";
        answer = answer.trim();
        if (answer.contains(NOT_FOUND_MARK)) {
            return new ChatDtos.AskResponse(answer, List.of(), false,
                    ranked.isEmpty() ? null : ranked.get(0).score(), "NO_DATA");
        }
        List<ChatDtos.Citation> citations = extractCitations(answer, ranked);
        if (citations.isEmpty()) {
            // 没有可验证的引用 -> 拒绝输出无法溯源的回答
            return new ChatDtos.AskResponse(
                    "抱歉，本次回答未能通过引用校验（未在召回资料中找到可核验的【来源】标记），因此不予展示。您可以重新提问或检查文档分块质量。",
                    List.of(), false, ranked.isEmpty() ? null : ranked.get(0).score(), "UNVERIFIED");
        }
        return new ChatDtos.AskResponse(answer, citations, true,
                ranked.isEmpty() ? null : ranked.get(0).score(), null);
    }

    /** 校验回答中的每个引用标记都能在 TopN 候选中找到对应的 文件名+页码 */
    private List<ChatDtos.Citation> extractCitations(String answer, List<RetrievalService.Candidate> ranked) {
        List<ChatDtos.Citation> result = new ArrayList<>();
        Map<String, Integer> order = new LinkedHashMap<>();
        for (RetrievalService.Candidate c : ranked) {
            String marker = marker(c.chunk().getDocName(), c.chunk().getPageNum());
            if (marker == null) continue;
            int idx = answer.indexOf(marker);
            if (idx >= 0 && !order.containsKey(marker)) {
                order.put(marker, idx);
                result.add(new ChatDtos.Citation(c.chunk().getChunkId(), c.chunk().getDocId(),
                        c.chunk().getDocName(), c.chunk().getPageNum(), c.chunk().getChapterTitle(),
                        truncate(c.chunk().getChunkText(), 400)));
            }
        }
        result.sort((a, b) -> Integer.compare(
                order.getOrDefault(marker(a.docName(), a.pageNum()), Integer.MAX_VALUE),
                order.getOrDefault(marker(b.docName(), b.pageNum()), Integer.MAX_VALUE)));
        return result;
    }

    private String marker(String docName, Integer page) {
        if (docName == null || page == null) return null;
        return "【来源：" + docName + "，第" + page + "页】";
    }

    /** 推送一个事件；返回 false 表示连接已断开 */
    private boolean emit(SseEmitter emitter, boolean[] cancelled, String event, Object payload) {
        if (cancelled[0]) return false;
        try {
            emitter.send(SseEmitter.event().name(event).data(mapper.writeValueAsString(payload)));
            return true;
        } catch (Exception e) {
            cancelled[0] = true;
            log.warn("SSE 推送中断 ({})", event);
            return false;
        }
    }

    private void safeEmit(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(mapper.writeValueAsString(payload)));
        } catch (Exception ignore) {
            // client gone
        }
    }

    private void saveLog(ChatDtos.AskRequest request, ChatDtos.AskResponse resp,
                         List<RetrievalService.Candidate> ranked, long costMs) {
        try {
            QaLog logRow = new QaLog();
            logRow.setQuestion(request.question());
            logRow.setAnswer(resp.answer());
            List<String> cited = resp.citations() == null ? List.of()
                    : resp.citations().stream().map(ChatDtos.Citation::chunkId).distinct().toList();
            logRow.setCitedChunkIds(mapper.writeValueAsString(cited));
            logRow.setRetrievalScore(resp.retrievalScore() == null ? null : resp.retrievalScore().floatValue());
            logRow.setResponseTimeMs((int) costMs);
            logRow.setUserId(request.userId());
            qaLogRepository.save(logRow);
        } catch (Exception e) {
            log.warn("写问答日志失败: {}", e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
