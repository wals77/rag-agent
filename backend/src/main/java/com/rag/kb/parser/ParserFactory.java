package com.rag.kb.parser;

import com.rag.kb.config.RagProperties;
import com.rag.kb.ocr.OcrClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ParserFactory {

    private final PdfParser pdf;
    private final WordParser word;
    private final TxtParser txt;
    private final MdParser md;

    public ParserFactory(RestClient.Builder builder, RagProperties props) {
        OcrClient ocrClient = new OcrClient(builder, props);
        this.pdf = new PdfParser(ocrClient, props);
        this.word = new WordParser();
        this.txt = new TxtParser();
        this.md = new MdParser();
    }

    /** 返回支持该文件名的解析器；不支持返回 null */
    public DocumentParser forFile(String filename) {
        if (pdf.supports(filename)) return pdf;
        if (word.supports(filename)) return word;
        if (md.supports(filename)) return md;
        if (txt.supports(filename)) return txt;
        return null;
    }
}
