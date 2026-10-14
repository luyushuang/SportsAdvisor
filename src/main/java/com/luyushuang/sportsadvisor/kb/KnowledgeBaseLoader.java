package com.luyushuang.sportsadvisor.kb;

import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeBaseLoader implements CommandLineRunner {

    private final VectorStore vectorStore;

    public KnowledgeBaseLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) {
        // 遍历 knowledge/ 下所有 Markdown 文件
        ClassPathResource root = new ClassPathResource("knowledge");
        List<Document> allChunks = new ArrayList<>();

        // 使用 TokenTextSplitter 按 Token 数量分块
        // 每块约 512 token，重叠 64 token，保证上下文连续性
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(512)
                .withMinChunkSizeChars(100)
                .withMinChunkLengthToEmbed(50)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();

        // 加载每个文档
        for (String file : List.of(
                "training-plans/half-marathon-12week.md",
                "injury-prevention/it-band-syndrome.md",
                "nutrition/pre-run-fueling.md",
                "pace-training/heart-rate-zones.md")) {

            TextReader reader = new TextReader(new ClassPathResource("knowledge/" + file));
            reader.getCustomMetadata().put("source", file);
            reader.getCustomMetadata().put("category",
                    file.contains("injury") ? "injury" :
                            file.contains("nutrition") ? "nutrition" :
                            file.contains("pace") ? "pace" : "training");

            List<Document> chunks = splitter.apply(reader.get());
            allChunks.addAll(chunks);
        }

        vectorStore.add(allChunks);
        System.out.println("知识库加载完成，共 " + allChunks.size() + " 个文档块");
    }
}
