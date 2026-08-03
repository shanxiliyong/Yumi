package yumi.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置
 * 使用内存存储向量，适合小规模知识库场景
 * 如需持久化，可替换为 RedisVectorStore 或 PgVectorStore
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // 使用 SimpleVectorStore（内存存储）
        // 生产环境建议替换为 RedisVectorStore 或 PgVectorStore
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}