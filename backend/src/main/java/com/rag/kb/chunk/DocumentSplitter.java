package com.rag.kb.chunk;

import com.rag.kb.config.RagProperties;
import com.rag.kb.parser.ParsedDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档拆分器基类：按文档类型选择不同的规则粗切（父块）策略，产出 RoughChunk 供 ChunkingEngine 加工。
 *
 * <p>扩展方式：新增一种文件类型的拆分策略时，继承本类并注册为 Spring Bean，实现
 * {@link #supports(String)} 与 {@link #split(ParsedDocument)} 即可，无需改动工厂；
 * 拆分器按 {@code @Order} 顺序被扫描，通用策略（RuleSplitter）作为最后兜底。</p>
 *
 * <p>共享的逐行扫描辅助（{@link #toLines}、{@link #flush}、超长按句回退 {@link #splitBySentence}、
 * 区间粗定位 {@link #pieceStart}）在基类统一提供。</p>
 */
public abstract class DocumentSplitter {

    /** 单个规则父块的最大字符数（超长内容回退切分） */
    protected final int maxChars;

    protected DocumentSplitter(RagProperties props) {
        this.maxChars = props.getChunking().getRuleMaxChars();
    }

    /** 该拆分器是否支持 <code>filename</code> 对应的文件类型（一般按扩展名判断）。 */
    public abstract boolean supports(String filename);

    /** 按本策略将解析后的文档切分为规则父块。 */
    public abstract List<RoughChunk> split(ParsedDocument doc);

    protected void flush(List<RoughChunk> out, List<Line> cur, int curLen,
                         int segLocalStart, int segLocalEnd, int pageGlobalStart,
                         int pageNum, String heading) {
        if (cur.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Line l : cur) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(l.text);
        }
        out.add(new RoughChunk(sb.toString(), pageNum, heading,
                pageGlobalStart + Math.max(0, segLocalStart),
                pageGlobalStart + Math.max(0, segLocalEnd)));
    }

    /** 将“疑似句子断点前内容”继续拆分 */
    protected List<String> splitBySentence(String text) {
        List<String> res = new ArrayList<>();
        String[] parts = text.split("(?<=[。！？；;])");
        StringBuilder cur = new StringBuilder();
        for (String p : parts) {
            if (cur.length() + p.length() > maxChars && cur.length() > 0) {
                res.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(p);
        }
        if (cur.length() > 0) res.add(cur.toString());
        return res;
    }

    /** 单行内容及其在页面内的字符起止位置 */
    protected static final class Line {
        final String text;
        final int start;
        final int end;

        Line(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    protected List<Line> toLines(String pageText) {
        List<Line> res = new ArrayList<>();
        if (pageText == null || pageText.isEmpty()) return res;
        int i = 0, n = pageText.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(pageText.charAt(i))) i++;
            if (i >= n) break;
            int start = i;
            while (i < n && pageText.charAt(i) != '\n') i++;
            int end = i;
            String content = pageText.substring(start, end).trim();
            if (!content.isEmpty()) {
                res.add(new Line(content.replaceAll("[\\t ]+", " "), start, end));
            }
            if (i < n && pageText.charAt(i) == '\n') i++;
        }
        return res;
    }

    /** 近似定位某一整段在 pageText 中的起点（用于粗定位，失败返回 -1） */
    protected int pieceStart(String pageText, String piece, int fallback) {
        int idx = pageText.indexOf(piece);
        return idx >= 0 ? idx : fallback;
    }
}