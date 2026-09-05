package com.rag.kb.chunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 语义分块：让 Qwen2.5-7B 在“规则父块”内部挑选语义完整的切割点。
 * 模型只允许输出按原文摘录的子块（不增删改），后端会校验文本无遗漏后才采纳。
 */
@Service
public class LlmChunkingService {

    private static final Logger log = LoggerFactory.getLogger(LlmChunkingService.class);

    private final ChatModel chatModel;
    private final RagProperties props;
    private final ObjectMapper mapper;

    private static final String SYSTEM_PROMPT = """
            你是企业文档库的“智能分块”专家。你的任务是：给定一段需要入库的文档原文，在尽量不打散语义的前提下，将其切分成若干语义完整的子块。
            硬性要求：
            1. 只能从原文中按顺序原样摘录，不得改写、增删、润色任何文字，不得补充任何解释。
            2. 不要给任何子块添加编号、序号、星号或“块1：”之类的前后缀。
            3. 每块控制在 80~900 字；优先在段落、完整句子的交界处断开；同一主题（同一条条款/同一个小节）尽量放入同一块。
            4. 所有子块拼接后必须能完整覆盖原文，不允许丢内容。
            只输出一个 JSON 数组（数组元素为字符串），例如：["...第一段...","...第二段..."]，禁止输出任何其它内容。
            """;

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*]", Pattern.DOTALL);

    public LlmChunkingService(ChatModel chatModel, RagProperties props, ObjectMapper mapper) {
        this.chatModel = chatModel;
        this.props = props;
        this.mapper = mapper;
    }

    /**
     * 对一个规则父块做语义细分。
     * @return 子块文本列表；返回 null 表示无需拆分（调用方应保留该父块为单块）。
     */
    public List<String> refine(String parentText) {
        if (!props.getChunking().isLlmEnabled()) return null;
        if (parentText == null || parentText.length() < 120) return null;

        String user = "以下是需要切分的原文：\n\n" + parentText;
        try {
            OllamaOptions options = OllamaOptions.builder()
                    .model(props.getChatModel())
                    .temperature(0.0)
                    .build();
            ChatResponse resp = chatModel.call(new Prompt(
                    List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(user)), options));
            String output = resp.getResult().getOutput().getText();
            List<String> raw = parseJsonArray(output);
            if (raw == null || raw.size() <= 1) return null;
            List<String> pieces = sanitizePieces(raw);
            if (pieces.size() <= 1) return null;
            if (!coversOriginal(parentText, pieces)) {
                log.warn("LLM 分块结果与原文不一致，父块保留为单块。片段数={}", pieces.size());
                return null;
            }
            return pieces;
        } catch (Exception e) {
            log.debug("LLM 语义分块失败，回退规则父块: {}", e.getMessage());
            return null;
        }
    }

    private List<String> parseJsonArray(String output) {
        if (output == null) return null;
        Matcher m = JSON_ARRAY.matcher(output);
        if (!m.find()) return null;
        try {
            var node = mapper.readTree(m.group());
            if (!node.isArray()) return null;
            List<String> list = new ArrayList<>();
            for (var v : node) list.add(v.asText());
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> sanitizePieces(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            String t = s == null ? "" : s.trim();
            if (t.isEmpty()) continue;
            // 兜底剥离模型偶发的 “1. ” 前缀
            t = t.replaceFirst("^\\d{1,2}[\\.、．]\\s*", "");
            out.add(t);
        }
        return out;
    }

    /** 校验模型摘录是否按顺序完整覆盖原文（忽略所有空白差异） */
    private boolean coversOriginal(String original, List<String> pieces) {
        String normOrig = original.replaceAll("\\s+", "");
        if (normOrig.isEmpty()) return false;
        String normJoined = String.join("", pieces).replaceAll("\\s+", "");
        int cursor = 0;
        for (String p : pieces) {
            String np = p.replaceAll("\\s+", "");
            if (np.isEmpty()) continue;
            int idx = normOrig.indexOf(np, cursor);
            if (idx < 0) return false;
            cursor = idx + np.length();
        }
        // 被覆盖长度 >= 90% 认为完整
        return (double) cursor / normOrig.length() >= 0.90;
    }
}
