package com.rag.kb.parser;

import java.io.IOException;

public interface DocumentParser {

    boolean supports(String filename);

    ParsedDocument parse(String filename, byte[] bytes) throws IOException;
}
