package com.rag.kb.parser;

import java.io.IOException;
import java.util.Locale;

/**
 * Markdown(.md/.markdown) 解析：与纯文本不同，整篇保留为单个页面、不按空行强行分页，
 * 使标题层级(ATX/setext)、代码块等结构位于同一条文本流，交给针对 md 的按标题拆分策略处理。
 * 保持原文（含 Markdown 标记）不剥离，保证引用溯源与预览一致性。
 */
public class MdParser extends TxtParser {

    @Override
    public boolean supports(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    @Override
    public ParsedDocument parse(String filename, byte[] bytes) throws IOException {
        ParsedDocument doc = new ParsedDocument(filename);
        String raw = decode(bytes);
        String text = raw.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (text.isEmpty()) {
            doc.addPage(1, "");
        } else {
            doc.addPage(1, text);
        }
        return doc;
    }
}