package com.rag.kb.chunk;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档拆分器工厂：按 {@link DocumentSplitter#supports(String)} 选取与文件类型匹配的拆分策略。
 * 拆分器按 {@code @Order} 排序注入（优先级从高到低），都不匹配时回退到列表最后的通用拆分器
 * （RuleSplitter）；后续新增文件类型只需新增一个 {@link DocumentSplitter} 实现类并声明
 * {@code supports()}，无需改动本类。
 */
@Component
public class SplitterFactory {

    private final List<DocumentSplitter> splitters;

    public SplitterFactory(List<DocumentSplitter> splitters) {
        this.splitters = splitters;
    }

    /** 返回支持该文件名的拆分器；无匹配时回退到通用拆分器，列表为空则返回 null。 */
    public DocumentSplitter forFile(String filename) {
        for (DocumentSplitter s : splitters) {
            if (s.supports(filename)) {
                return s;
            }
        }
        return splitters.isEmpty() ? null : splitters.get(splitters.size() - 1);
    }
}