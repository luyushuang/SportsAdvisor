package com.luyushuang.sportsadvisor.kb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class KnowledgeBaseLoader implements ApplicationRunner {

    private static final String VERSION_KEY = "knowledge:loaded-version";

    private final VectorStore vectorStore;
    private final StringRedisTemplate redisTemplate;
    private final String knowledgePath;
    private final String knowledgeVersion;

    public KnowledgeBaseLoader(VectorStore vectorStore,
                               StringRedisTemplate redisTemplate,
                               @Value("${app.knowledge.path}") String knowledgePath,
                               @Value("${app.knowledge.version}") String knowledgeVersion) {
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;
        this.knowledgePath = knowledgePath;
        this.knowledgeVersion = knowledgeVersion;
    }

    @Override
    public void run(ApplicationArguments args) {
        String loadedVersion = redisTemplate.opsForValue().get(VERSION_KEY);

        if (knowledgeVersion.equals(loadedVersion)) {
            log.info("知识库版本 [{}] 已加载，跳过...", loadedVersion);
            return;
        }

        log.info("检测到知识库版本变化，旧版本: {}，新版本：{}，开始加载...", loadedVersion, knowledgeVersion);

        // 1. 清空旧数据（避免重复）
        // 只删除旧版本的文档
        deleteOldVersion();

        // 2. 加载新文档
        List<Document> chunks = loadDocuments();

        // 3. 写入向量库
        vectorStore.add(chunks);

        // 4. 更新版本标记
        redisTemplate.opsForValue().set(VERSION_KEY, knowledgeVersion);

        log.info("知识库加载完成，共{}个文档块", chunks.size());
    }

    private void deleteOldVersion() {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        // 循环删除，直到没有匹配的文档为止
        while (true) {
            Filter.Expression filter = b.ne("version", knowledgeVersion).build();
            // 先检索确认是否还有匹配文档
            List<Document> remaining = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("") // 空查询，仅用于触发过滤
                            .filterExpression(filter)
                            .topK(100)
                            .build()
            );
            if (remaining.isEmpty()) break;
            vectorStore.delete(filter);
        }
    }

    private List<Document> loadDocuments() {
        List<Document> allChunks = new ArrayList<>();

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(512)
                .withMinChunkSizeChars(100)
                .withMinChunkLengthToEmbed(50)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();

        // 遍历知识库目录下的所有 Markdown 文件
        for (String file : scanMarkdownFiles()) {
            TextReader reader = new TextReader(new ClassPathResource(file));
            reader.getCustomMetadata().put("source", file);
            reader.getCustomMetadata().put("category", inferCategory(file));
            reader.getCustomMetadata().put("version", knowledgeVersion);

            List<Document> chunks = splitter.apply(reader.get());
            allChunks.addAll(chunks);
        }

        return allChunks;
    }

    private List<String> scanMarkdownFiles() {
        // 简化实现：实际可以用 ResourcePatternResolver 扫描目录
        return List.of(
                "knowledge/training-plans/half-marathon-12week.md",
                "knowledge/injury-prevention/it-band-syndrome.md",
                "knowledge/injury-prevention/plantar-fasciitis.md",
                "knowledge/nutrition/pre-during-post-run-fueling.md",
                "knowledge/pace-training/heart-rate-zones.md"
        );
    }

    private String inferCategory(String file) {
        if (file.contains("injury")) return "injury-prevention";
        if (file.contains("nutrition")) return "nutrition";
        if (file.contains("pace")) return "pace-training";
        return "training";
    }
}
