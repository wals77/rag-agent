package com.rag.kb.doc;

import com.rag.kb.config.RagProperties;
import com.rag.kb.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(RagProperties props) {
        this.root = Paths.get(props.getFileStoreDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建文件存储目录: " + root, e);
        }
    }

    public String store(MultipartFile file, String docId) throws IOException {
        String ext = extension(file.getOriginalFilename());
        Path target = root.resolve(docId + ext).normalize();
        if (!target.startsWith(root)) {
            throw new BizException("非法文件名");
        }
        Files.copy(file.getInputStream(), target);
        return target.toString();
    }

    public Path load(String storedPath) {
        Path p = Paths.get(storedPath).toAbsolutePath().normalize();
        if (!Files.exists(p)) {
            throw new BizException("文件不存在: " + storedPath);
        }
        return p;
    }

    public void delete(String storedPath) {
        try {
            if (storedPath != null && !storedPath.isBlank()) {
                Files.deleteIfExists(Paths.get(storedPath));
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "";
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    public String contentType(String filename) {
        String ext = extension(filename);
        return switch (ext) {
            case ".pdf" -> "application/pdf";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".doc" -> "application/msword";
            case ".txt", ".md", ".text" -> "text/plain; charset=utf-8";
            default -> "application/octet-stream";
        };
    }
}
