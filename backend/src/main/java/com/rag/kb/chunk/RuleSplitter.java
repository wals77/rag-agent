package com.rag.kb.chunk;

import com.rag.kb.config.RagProperties;
import com.rag.kb.parser.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 规则粗切（父块）：按页扫描，遇到章/节标题或达到单块最大字符后结束当前块。
 * 同时记录父块在全文中的全局字符区间、页码、章节标题。
 */
@Component
public class RuleSplitter {

    private final int maxChars;

    public RuleSplitter(RagProperties props) {
        this.maxChars = props.getChunking().getRuleMaxChars();
    }

    private static final Pattern HEADING =
            Pattern.compile("^(?:第\\s*[0-9一二三四五六七八九十百千零两]+\\s*[章节卷篇部课]"
                    + "|第\\s*[0-9一二三四五六七八九十百千零两]+\\s*节"
                    + "|\\d{1,2}(?:\\.\\d{1,3}){0,3}[\\.、．\\s]\\S{1,60}"
                    + "|[一二三四五六七八九十百千]+[、．.]\\S{1,60}"
                    + "|(?:附录|附件|前言|引言|摘要|目录|结[论语]|参考文献|致谢)[:：]?\\s*\\S{0,40})");

    public List<RoughChunk> split(ParsedDocument doc) {
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

    private void flush(List<RoughChunk> out, List<Line> cur, int curLen,
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
    private List<String> splitBySentence(String text) {
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

    private boolean isHeading(String line) {
        if (line == null) return false;
        String t = line.trim();
        if (t.length() > 80) return false;
        if (t.isEmpty()) return false;
        return HEADING.matcher(t).matches();
    }

    private record Line(String text, int start, int end) {}

    private List<Line> toLines(String pageText) {
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
    private int pieceStart(String pageText, String piece, int fallback) {
        int idx = pageText.indexOf(piece);
        return idx >= 0 ? idx : fallback;
    }
}
