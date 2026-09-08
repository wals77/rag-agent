package com.rag.kb.chunk;

import com.rag.kb.config.RagProperties;
import com.rag.kb.parser.ParsedDocument;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 通用规则粗切（父块）：适用于 PDF / DOCX / TXT 等非结构化文本，按页扫描，
 * 遇到章/节标题或达到单块最大字符后结束当前块；同时记录父块在全文中的
 * 全局字符区间、页码、章节标题。
 *
 * <p>作为拆分器列表的最后兜底（{@code @Order(LOWEST_PRECEDENCE)}）：新的文件类型
 * 如需专用拆分，优先新建 {@link DocumentSplitter} 实现并通过 {@code supports()} 声明，
 * 本类不认领未在其 {@link #supports(String)} 中列出的类型。</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RuleSplitter extends DocumentSplitter {

    private static final Pattern HEADING =
            Pattern.compile("^(?:第\\s*[0-9一二三四五六七八九十百千零两]+\\s*[章节卷篇部课]"
                    + "|第\\s*[0-9一二三四五六七八九十百千零两]+\\s*节"
                    + "|\\d{1,2}(?:\\.\\d{1,3}){0,3}[\\.、．\\s]\\S{1,60}"
                    + "|[一二三四五六七八九十百千]+[、．.]\\S{1,60}"
                    + "|(?:附录|附件|前言|引言|摘要|目录|结[论语]|参考文献|致谢)[:：]?\\s*\\S{0,40})");

    public RuleSplitter(RagProperties props) {
        super(props);
    }

    @Override
    public boolean supports(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.endsWith(".docx")
                || lower.endsWith(".txt") || lower.endsWith(".text");
    }

    @Override
    public List<RoughChunk> split(ParsedDocument doc) {
        return splitGeneric(doc);
    }

    /** 通用拆分策略：逐页扫描，遇到章/节标题或达到单块最大字符后结束当前块。 */
    private List<RoughChunk> splitGeneric(ParsedDocument doc) {
        List<RoughChunk> result = new ArrayList<>();
        int globalCursor = 0;
        String activeHeading = null;

        for (ParsedDocument.ParsedPage page : doc.getPages()) {
            int pageGlobalStart = globalCursor;
            String pageText = page.getText() == null ? "" : page.getText().trim();
            List<Line> lines = toLines(pageText);

            List<Line> cur = new ArrayList<>();
            int curLen = 0;
            int segLocalStart = -1;
            int segLocalEnd = -1;

            for (Line line : lines) {
                String t = line.text;
                if (isHeading(t)) {
                    flush(result, cur, curLen, segLocalStart, segLocalEnd, pageGlobalStart,
                            page.getPageNum(), activeHeading);
                    cur = new ArrayList<>();
                    curLen = 0;
                    segLocalStart = -1;
                    segLocalEnd = -1;
                    activeHeading = t;
                    continue;
                }
                if (curLen > 0 && curLen + 1 + t.length() > maxChars) {
                    flush(result, cur, curLen, segLocalStart, segLocalEnd, pageGlobalStart,
                            page.getPageNum(), activeHeading);
                    cur = new ArrayList<>();
                    curLen = 0;
                    segLocalStart = -1;
                    segLocalEnd = -1;
                }
                // 超长单行按句子二次切断
                if (t.length() > maxChars && curLen == 0) {
                    for (String piece : splitBySentence(t)) {
                        if (curLen > 0 && curLen + 1 + piece.length() > maxChars) {
                            flush(result, cur, curLen, segLocalStart, segLocalEnd, pageGlobalStart,
                                    page.getPageNum(), activeHeading);
                            cur = new ArrayList<>();
                            curLen = 0;
                            segLocalStart = -1;
                            segLocalEnd = -1;
                        }
                        if (segLocalStart < 0) segLocalStart = pieceStart(pageText, piece, segLocalStart);
                        cur.add(new Line(piece, 0, piece.length()));
                        curLen += (curLen == 0 ? 0 : 1) + piece.length();
                        segLocalEnd = line.end;
                    }
                    continue;
                }
                if (segLocalStart < 0) segLocalStart = line.start;
                cur.add(line);
                curLen += (curLen == 0 ? 0 : 1) + line.text.length();
                segLocalEnd = line.end;
            }
            flush(result, cur, curLen, segLocalStart, segLocalEnd, pageGlobalStart,
                    page.getPageNum(), activeHeading);

            globalCursor = pageGlobalStart + pageText.length() + 1; // +1 页间分隔符
        }
        return result;
    }

    private boolean isHeading(String line) {
        if (line == null) return false;
        String t = line.trim();
        if (t.length() > 80) return false;
        if (t.isEmpty()) return false;
        return HEADING.matcher(t).matches();
    }
}