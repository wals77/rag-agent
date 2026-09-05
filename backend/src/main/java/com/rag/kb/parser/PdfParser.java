package com.rag.kb.parser;

import com.rag.kb.config.RagProperties;
import com.rag.kb.ocr.OcrClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * PDF 解析：优先提取文本层；页面无可提取文本（扫描件）时，若有 OCR 服务则渲染成图片调用 PaddleOCR。
 */
public class PdfParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfParser.class);

    private final OcrClient ocrClient;
    private final RagProperties props;

    public PdfParser(OcrClient ocrClient, RagProperties props) {
        this.ocrClient = ocrClient;
        this.props = props;
    }

    @Override
    public boolean supports(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    @Override
    public ParsedDocument parse(String filename, byte[] bytes) throws IOException {
        ParsedDocument doc = new ParsedDocument(filename);
        try (PDDocument pdf = PDDocument.load(bytes)) {
            if (pdf.isEncrypted()) {
                try {
                    pdf.setAllSecurityToBeRemoved(false);
                } catch (Exception e) {
                    throw new IOException("PDF 已加密且无法自动解密: " + e.getMessage());
                }
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");

            boolean ocrAvailable = props.getOcr().getBaseUrl() != null && !props.getOcr().getBaseUrl().isBlank();
            int total = pdf.getNumberOfPages();

            for (int i = 1; i <= total; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = normalize(stripper.getText(pdf));

                if (text.isBlank() && ocrAvailable) {
                    byte[] png = renderPage(pdf, i);
                    if (png != null) {
                        String ocrText = ocrClient.recognize(filename, i, png);
                        if (ocrText != null && !ocrText.isBlank()) {
                            text = normalize(ocrText);
                            doc.setOcrUsed(true);
                        }
                    }
                }
                doc.addPage(i, text);
            }
        }
        return doc;
    }

    private byte[] renderPage(PDDocument pdf, int pageNo) {
        try {
            PDFRenderer renderer = new PDFRenderer(pdf);
            java.awt.image.BufferedImage img = renderer.renderImageWithDPI(pageNo - 1, 150, ImageType.RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("渲染 PDF 第 {} 页失败: {}", pageNo, e.getMessage());
            return null;
        }
    }

    private String normalize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\r\n", "\n")
                .replaceAll("\r", "\n")
                .replaceAll("\\u000c", "\n")
                .trim();
    }
}
