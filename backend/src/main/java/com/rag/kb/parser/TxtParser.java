package com.rag.kb.parser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class TxtParser implements DocumentParser {

    @Override
    public boolean supports(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".text");
    }

    @Override
    public ParsedDocument parse(String filename, byte[] bytes) throws IOException {
        ParsedDocument doc = new ParsedDocument(filename);
        String raw = decode(bytes);
        // 常见分页分隔：多个换行/换页
        String[] pages = raw.split("\\n\\s*\\n\\s*\\n+");
        int idx = 0;
        for (String p : pages) {
            if (!p.isBlank()) {
                doc.addPage(++idx, p.trim());
            }
        }
        if (doc.totalPages() == 0) {
            doc.addPage(1, "");
        }
        return doc;
    }

    protected String decode(byte[] bytes) {
        for (Charset cs : new Charset[]{StandardCharsets.UTF_8, Charset.forName("GBK"), StandardCharsets.ISO_8859_1}) {
            try {
                String s = new String(bytes, cs);
                // 简单启发：UTF-8 解码后若出现替换符则尝试下一编码
                if (cs == StandardCharsets.UTF_8 && s.contains("\uFFFD")) continue;
                return s;
            } catch (Exception ignore) {
                // continue
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
