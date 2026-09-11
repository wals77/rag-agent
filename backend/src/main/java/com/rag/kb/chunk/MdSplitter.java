package com.rag.kb.chunk;

import com.rag.kb.config.RagProperties;
import com.rag.kb.parser.ParsedDocument;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 结构化拆分器（commonmark AST 驱动，不使用 LLM）：
 *
 * <ul>
 *   <li>以二级标题（##）为最小逻辑单元：每个 ## 及其下所有内容（含子标题、代码块、表格）
 *       作为一个独立父章节（Chunk）；</li>
 *   <li>内容头部强制注入完整章节路径（【章节路径】A &gt; B &gt; C），让模型理解上下文归属；</li>
 *   <li>文档没有 ## 时降级按 ### 切；连标题都没有时回退按段落切（每 2-3 段一块）；</li>
 *   <li>超过单块上限的父章节：先产出父章节整体块（sectionParent，落库 status=split 不参与检索），
 *       再按下一级标题拆为子块，仍超长再按段落分组；同一父章节的子块共享 sectionKey，
 *       检索命中任一子块时可扩展整章上下文。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdSplitter extends DocumentSplitter {

    private static final Pattern ATX_PREFIX = Pattern.compile("^\\s{0,3}#{1,6}\\s+");
    private static final Pattern ATX_SUFFIX = Pattern.compile("\\s+#+\\s*$");
    private static final String PATH_SEPARATOR = " > ";
    private static final String PATH_PREFIX = "【章节路径】";
    private static final int MAX_CHAPTER_TITLE_LEN = 250;

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS)
            .build();

    public MdSplitter(RagProperties props) {
        super(props);
    }

    @Override
    public boolean supports(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    @Override
    public List<RoughChunk> split(ParsedDocument doc) {
        String raw = joinPages(doc);
        if (raw.isBlank()) return new ArrayList<>();
        return splitAst(raw);
    }

    private String joinPages(ParsedDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (ParsedDocument.ParsedPage page : doc.getPages()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(page.getText() == null ? "" : page.getText().trim());
        }
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------- AST 拆分

    private List<RoughChunk> splitAst(String raw) {
        List<Seg> segs = collectSegs(raw);
        if (segs.isEmpty()) return new ArrayList<>();

        int splitLevel = 0;
        for (Seg s : segs) {
            if (s.level == 2) { splitLevel = 2; break; }
        }
        if (splitLevel == 0) {
            for (Seg s : segs) {
                if (s.level == 3) { splitLevel = 3; break; }
            }
        }

        List<RoughChunk> out = new ArrayList<>();
        if (splitLevel == 0) {
            // 无标题：回退按段落切（每 2-3 段一块）
            paragraphChunks(out, segs, docTitle(segs), null, null);
            return out;
        }
        splitBySections(out, segs, raw, splitLevel);
        return out;
    }

    /** 按 splitLevel（## 或 ###）切父章节，超长父章节再拆子块 */
    private void splitBySections(List<RoughChunk> out, List<Seg> segs, String raw, int splitLevel) {
        List<Section> sections = buildSections(segs, raw, splitLevel);
        for (Section sec : sections) {
            String content = raw.substring(sec.start(), sec.end()).trim();
            if (content.isEmpty()) continue;

            if (content.length() <= maxChars) {
                out.add(chunk(sec.path(), sec.level(), sec.path(), content, sec.start(), sec.end(), false));
                continue;
            }

            // 超长父章节：整体块作为父节点（status=split），子块参与检索
            out.add(chunk(sec.path(), sec.level(), sec.path(), content, sec.start(), sec.end(), true));

            int subLevel = (sec.level() == null ? 1 : sec.level()) + 1;
            List<Seg> inner = segsInRange(segs, sec.start(), sec.end());
            List<int[]> subRanges = new ArrayList<>();
            List<String> subTitles = new ArrayList<>();
            int curStart = sec.start();
            String curTitle = null;
            for (Seg s : inner) {
                if (s.level == subLevel) {
                    if (curStart < s.start) {
                        subRanges.add(new int[]{curStart, s.start});
                        subTitles.add(curTitle);
                    }
                    curStart = s.start;
                    curTitle = s.title;
                }
            }
            if (curStart < sec.end()) {
                subRanges.add(new int[]{curStart, sec.end()});
                subTitles.add(curTitle);
            }

            boolean hasSubHeadings = subTitles.stream().anyMatch(t -> t != null);
            if (!hasSubHeadings) {
                // 无下一级标题：按段落分组
                paragraphChunks(out, inner, sec.path(), sec.level(), sec.path());
                continue;
            }
            for (int i = 0; i < subRanges.size(); i++) {
                int[] range = subRanges.get(i);
                String title = subTitles.get(i);
                String subContent = raw.substring(range[0], range[1]).trim();
                if (subContent.isEmpty()) continue;
                String subPath = title == null ? sec.path() : sec.path() + PATH_SEPARATOR + title;
                Integer subBlockLevel = title == null ? sec.level() : subLevel;
                if (subContent.length() <= maxChars) {
                    out.add(chunk(subPath, subBlockLevel, sec.path(), subContent, range[0], range[1], false));
                } else {
                    paragraphChunks(out, segsInRange(segs, range[0], range[1]), subPath, subBlockLevel, sec.path());
                }
            }
        }
    }

    /** 扫描顶层块，维护标题栈，切出 splitLevel 级父章节区间（含文档开头的前言部分） */
    private List<Section> buildSections(List<Seg> segs, String raw, int splitLevel) {
        List<Section> sections = new ArrayList<>();
        List<HeadingInfo> stack = new ArrayList<>();
        String preamblePath = null;
        Integer preambleLevel = null;
        int sectionStart = -1;
        String sectionPath = null;

        for (Seg s : segs) {
            if (s.level <= 0) continue;
            while (!stack.isEmpty() && stack.get(stack.size() - 1).level >= s.level) {
                stack.remove(stack.size() - 1);
            }
            stack.add(new HeadingInfo(s.level, s.title));
            if (s.level < splitLevel) {
                preamblePath = joinTitles(stack, s.level);
                preambleLevel = s.level;
            }
            if (s.level == splitLevel) {
                if (sectionStart >= 0) {
                    sections.add(new Section(sectionPath, splitLevel, sectionStart, s.start));
                }
                sectionStart = s.start;
                sectionPath = joinTitles(stack, splitLevel);
            }
        }
        if (sectionStart >= 0) {
            sections.add(new Section(sectionPath, splitLevel, sectionStart, raw.length()));
        }
        if (!sections.isEmpty()) {
            int firstStart = sections.get(0).start();
            if (firstStart > 0) {
                sections.add(0, new Section(preamblePath, preambleLevel, 0, firstStart));
            }
        }
        return sections;
    }

    /** 段落分组：每 2-3 段一块，同时受单块上限约束；代码块/表格保持完整不切 */
    private void paragraphChunks(List<RoughChunk> out, List<Seg> blocks, String path,
                                 Integer level, String sectionKey) {
        List<Seg> acc = new ArrayList<>();
        int accChars = 0;
        for (Seg s : blocks) {
            boolean overLimit = accChars + 1 + s.content.length() > maxChars;
            if (!acc.isEmpty() && (acc.size() >= 3 || overLimit)) {
                flushParagraphGroup(out, acc, path, level, sectionKey);
                acc = new ArrayList<>();
                accChars = 0;
            }
            if (s.content.length() > maxChars && acc.isEmpty()) {
                if (s.preserve) {
                    out.add(chunk(path, level, sectionKey, s.content, s.start, s.end, false));
                } else {
                    for (String piece : splitBySentence(s.content)) {
                        out.add(chunk(path, level, sectionKey, piece, s.start, s.end, false));
                    }
                }
                continue;
            }
            acc.add(s);
            accChars += (accChars == 0 ? 0 : 1) + s.content.length();
        }
        flushParagraphGroup(out, acc, path, level, sectionKey);
    }

    private void flushParagraphGroup(List<RoughChunk> out, List<Seg> acc, String path,
                                     Integer level, String sectionKey) {
        if (acc.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Seg s : acc) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(s.content);
        }
        int start = acc.get(0).start;
        int end = acc.get(acc.size() - 1).end;
        out.add(chunk(path, level, sectionKey, sb.toString(), start, end, false));
    }

    // ---------------------------------------------------------------- AST 收集

    private List<Seg> collectSegs(String raw) {
        int[] lineStarts = lineStarts(raw);
        List<Seg> segs = new ArrayList<>();
        Node root = PARSER.parse(raw);
        for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
            int[] span = spanOf(child, raw, lineStarts);
            if (span == null) continue;
            String content = raw.substring(span[0], span[1]).trim();
            if (content.isEmpty()) continue;
            boolean heading = child instanceof Heading;
            int level = heading ? ((Heading) child).getLevel() : 0;
            segs.add(new Seg(span[0], span[1], content, level,
                    heading ? headingTitle(content) : null, isPreserveBlock(child)));
        }
        if (segs.isEmpty()) {
            // 无源码位置（异常情况）：按空行分块退化处理
            segs = fallbackSegs(raw);
        }
        segs.sort(Comparator.comparingInt(s -> s.start));
        return segs;
    }

    private List<Seg> fallbackSegs(String raw) {
        List<Seg> segs = new ArrayList<>();
        int n = raw.length();
        int i = 0;
        while (i < n) {
            while (i < n && Character.isWhitespace(raw.charAt(i))) i++;
            if (i >= n) break;
            int start = i;
            while (i < n && !(raw.charAt(i) == '\n' && i + 1 < n && raw.charAt(i + 1) == '\n')) i++;
            int end = Math.min(i + 1, n);
            String content = raw.substring(start, end).trim();
            if (!content.isEmpty()) {
                segs.add(new Seg(start, end, content, 0, null, false));
            }
        }
        return segs;
    }

    private int[] spanOf(Node node, String raw, int[] lineStarts) {
        var spans = node.getSourceSpans();
        if (spans == null || spans.isEmpty()) return null;
        var first = spans.get(0);
        var last = spans.get(spans.size() - 1);
        if (first.getLineIndex() >= lineStarts.length || last.getLineIndex() >= lineStarts.length) return null;
        int start = lineStarts[first.getLineIndex()] + first.getColumnIndex();
        int end = lineStarts[last.getLineIndex()] + last.getColumnIndex() + last.getLength();
        if (start < 0 || end > raw.length() || end <= start) return null;
        return new int[]{start, end};
    }

    private int[] lineStarts(String raw) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '\n') starts.add(i + 1);
        }
        int[] arr = new int[starts.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = starts.get(i);
        return arr;
    }

    private boolean isPreserveBlock(Node node) {
        return node instanceof org.commonmark.node.FencedCodeBlock
                || node instanceof org.commonmark.node.IndentedCodeBlock
                || node instanceof TableBlock;
    }

    /** 从 ATX 标题行提取标题文本 */
    private String headingTitle(String line) {
        String t = ATX_PREFIX.matcher(line).replaceFirst("");
        t = ATX_SUFFIX.matcher(t).replaceFirst("");
        t = t.trim();
        return t.isEmpty() ? "未命名章节" : t;
    }

    private String docTitle(List<Seg> segs) {
        for (Seg s : segs) {
            if (s.level == 1) return s.title;
        }
        return null;
    }

    private String joinTitles(List<HeadingInfo> stack, int maxLevel) {
        StringBuilder sb = new StringBuilder();
        for (HeadingInfo h : stack) {
            if (h.level > maxLevel) continue;
            if (sb.length() > 0) sb.append(PATH_SEPARATOR);
            sb.append(h.title);
        }
        return sb.toString();
    }

    private List<Seg> segsInRange(List<Seg> segs, int start, int end) {
        List<Seg> res = new ArrayList<>();
        for (Seg s : segs) {
            if (s.start >= start && s.end <= end) res.add(s);
        }
        return res;
    }

    /** 生成块：内容头部注入章节路径；rawContent 保留未注入的原始 Markdown；chapterTitle 保留尾部（防超长） */
    private RoughChunk chunk(String path, Integer level, String sectionKey,
                             String content, int start, int end, boolean sectionParent) {
        String text = (path == null || path.isBlank())
                ? content
                : PATH_PREFIX + path + "\n" + content;
        return new RoughChunk(text, 1, truncateTail(path), start, end, level, sectionKey, sectionParent, content);
    }

    private String truncateTail(String path) {
        if (path == null) return null;
        if (path.length() <= MAX_CHAPTER_TITLE_LEN) return path;
        return "…" + path.substring(path.length() - MAX_CHAPTER_TITLE_LEN + 1);
    }

    /** 顶层块片段 */
    private record Seg(int start, int end, String content, int level, String title, boolean preserve) {}

    private record HeadingInfo(int level, String title) {}

    /** 父章节区间（path=完整章节路径） */
    private record Section(String path, Integer level, int start, int end) {}
}