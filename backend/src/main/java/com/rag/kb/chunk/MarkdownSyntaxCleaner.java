package com.rag.kb.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 语法清洗器：产出供向量库与大模型消费的自然语言文本。
 *
 * <p>清洗顺序（表格必须最先执行，避免 | 被后续规则误伤）：</p>
 * <ol>
 *   <li>{@link #cleanMarkdownTable}：表格 -> 「表格行：表头1=值1，表头2=值2」自然语言描述</li>
 *   <li>其余语法符号清洗（标题/列表/加粗/链接/代码围栏等），代码围栏内部不清洗</li>
 * </ol>
 */
public final class MarkdownSyntaxCleaner {

    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s*(.+?)\\s*#*\\s*$");
    private static final Pattern LINK_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");
    private static final Pattern TABLE_SEPARATOR_CELL = Pattern.compile("^:?-+:?$");
    private static final String[] HEADING_NAMES = {"一级", "二级", "三级", "四级", "五级", "六级"};

    private MarkdownSyntaxCleaner() {}

    /** 完整清洗入口：表格先行，其余语法随后 */
    public static String clean(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) return "";
        String tableCleaned = cleanMarkdownTable(rawContent);
        return cleanOtherSyntax(tableCleaned);
    }

    /**
     * Markdown 表格清洗：
     * <ul>
     *   <li>识别以 | 开头（以 | 结尾）的表格行；</li>
     *   <li>分隔行（| :--- | :--- |）直接跳过；</li>
     *   <li>表格第一行作为表头，后续每行转为「表格行：表头1=值1，表头2=值2」；</li>
     *   <li>表格结束后清空表头，避免影响后续内容；</li>
     *   <li>代码围栏内部的类表格行不处理。</li>
     * </ul>
     */
    public static String cleanMarkdownTable(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) return rawContent == null ? "" : rawContent;
        String[] lines = rawContent.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean inFence = false;
        List<String> header = null;

        for (String line : lines) {
            if (FENCE.matcher(line).find()) {
                inFence = !inFence;
                out.append(line).append('\n');
                continue;
            }
            if (inFence) {
                out.append(line).append('\n');
                continue;
            }

            String trimmed = line.trim();
            boolean tableRow = trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() >= 2;

            if (!tableRow) {
                // 表格结束，清空表头
                header = null;
                out.append(line).append('\n');
                continue;
            }

            List<String> cells = splitRow(trimmed);
            if (isSeparatorRow(cells)) {
                if (header == null) {
                    // 分隔行出现在表头之前：跳过，不视为表头
                }
                continue;
            }
            if (header == null) {
                header = cells;
                continue;
            }
            out.append(renderRow(header, cells)).append('\n');
        }
        return out.toString();
    }

    /** 数据行 -> 表格行：表头1=值1，表头2=值2 */
    private static String renderRow(List<String> header, List<String> cells) {
        StringBuilder sb = new StringBuilder("表格行：");
        int n = Math.max(header.size(), cells.size());
        boolean first = true;
        for (int i = 0; i < n; i++) {
            String key = i < header.size() ? header.get(i) : "列" + (i + 1);
            String value = i < cells.size() ? cells.get(i) : "";
            if (key.isEmpty() && value.isEmpty()) continue;
            if (!first) sb.append('，');
            sb.append(key).append('=').append(value);
            first = false;
        }
        return sb.toString();
    }

    /** 去掉首尾 | 后按 | 切分单元格并去除首尾空白 */
    private static List<String> splitRow(String tableRow) {
        String inner = tableRow.substring(1, tableRow.length() - 1);
        String[] parts = inner.split("\\|", -1);
        List<String> cells = new ArrayList<>(parts.length);
        for (String p : parts) cells.add(p.trim());
        return cells;
    }

    /** 分隔行：每个单元格形如 --- / :--- / ---: / :---: */
    private static boolean isSeparatorRow(List<String> cells) {
        if (cells.isEmpty()) return false;
        for (String c : cells) {
            if (c.isEmpty() || !TABLE_SEPARATOR_CELL.matcher(c).matches()) return false;
        }
        return true;
    }

    /** 其余语法清洗（逐行、代码围栏感知） */
    private static String cleanOtherSyntax(String rawContent) {
        String[] lines = rawContent.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean inFence = false;
        for (String line : lines) {
            String trimmedStart = line.stripLeading();
            if (trimmedStart.startsWith("```") || trimmedStart.startsWith("~~~")) {
                inFence = !inFence; // 去掉代码块标记行，保留内容
                continue;
            }
            if (inFence) {
                out.append(line).append('\n'); // 代码内容原样保留
                continue;
            }
            String t = line;

            // 表格分隔行（若未被表格清洗覆盖的残留形态）直接去掉
            if (t.contains("|") && t.contains("-") && t.matches("^\\s*\\|?[\\s:|-]+\\|?[\\s:|-]*$")) {
                continue;
            }
            Matcher m = HEADING.matcher(t);
            if (m.matches()) {
                t = "【" + HEADING_NAMES[Math.min(6, Math.max(1, m.group(1).length())) - 1] + "标题】" + m.group(2);
            } else {
                t = t.replaceFirst("^\\s*[-*+]\\s+", "列表项：");
            }
            t = LINK_IMAGE.matcher(t).replaceAll("");
            t = LINK.matcher(t).replaceAll("$1");
            t = t.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
            t = t.replaceAll("\\*(.+?)\\*", "$1");
            t = t.replaceAll("__(.+?)__", "$1");
            t = t.replaceAll("_(.+?)_", "$1");
            t = t.replaceAll("`([^`]+)`", "$1");
            out.append(t).append('\n');
        }
        String cleaned = out.toString();
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned.trim();
    }
}