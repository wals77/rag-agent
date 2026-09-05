package com.rag.kb.util;

import java.util.UUID;

public final class IdGen {

    private IdGen() {}

    public static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String docId(String docName) {
        String base = sanitize(docName);
        String suffix = base.length() > 12 ? base.substring(0, 12) : base;
        return suffix + "_" + shortUuid().substring(0, 10);
    }

    public static String chunkId(String docId, String tag) {
        return docId + "_c_" + tag + shortUuid().substring(0, 8);
    }

    private static String sanitize(String name) {
        return name == null ? "doc" : name.replaceAll("[^\\w\\u4e00-\\u9fa5.-]", "_");
    }
}
