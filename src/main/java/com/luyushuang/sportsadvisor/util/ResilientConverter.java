package com.luyushuang.sportsadvisor.util;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.MarkdownCodeBlockCleaner;
import org.springframework.ai.converter.StructuredOutputConverter;

public class ResilientConverter<T> implements StructuredOutputConverter<T> {

    private final BeanOutputConverter<T> delegate;
    private final MarkdownCodeBlockCleaner markdownCleaner = new MarkdownCodeBlockCleaner();

    public ResilientConverter(Class<T> type) {
        this.delegate = new BeanOutputConverter<>(type);
    }

    @Override
    public T convert(String raw) {
        // 第 1 层：直接尝试
        try {
            return delegate.convert(raw);
        } catch (Exception ignored) { }

        // 第 2 层：剥离 Markdown 后重试
        String cleaned = markdownCleaner.clean(raw);
        try {
            return delegate.convert(cleaned);
        } catch (Exception ignored) { }

        // 第 3 层：截取第一个 { 到最后一个 } 之间内容
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String extracted = raw.substring(start, end + 1);
            try {
                return delegate.convert(extracted);
            } catch (Exception ignored) { }
        }

        // 全部失败，抛出原始异常，让上层（StructuredOutputValidationAdvisor）触发 LLM 重试
        return delegate.convert(raw);
    }

    @Override
    public String getFormat() {
        return delegate.getFormat();
    }
}
