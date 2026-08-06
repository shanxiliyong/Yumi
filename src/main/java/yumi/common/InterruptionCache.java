package yumi.common;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中断元数据内存缓存
 * Key: threadId + ":" + nodeId
 * Value: InterruptionMetadata
 */
@Slf4j
@Component
public class InterruptionCache {

    private final Map<String, InterruptionMetadata> cache = new ConcurrentHashMap<>();

    /**
     * 生成缓存 key
     */
    public static String buildKey(String threadId, String nodeId) {
        return threadId + ":" + nodeId;
    }

    /**
     * 存储中断元数据
     */
    public void put(String threadId, String nodeId, InterruptionMetadata metadata) {
        String key = buildKey(threadId, nodeId);
        cache.put(key, metadata);
        log.info("缓存中断元数据: key={}", key);
    }

    /**
     * 获取并移除中断元数据
     */
    public InterruptionMetadata getAndRemove(String threadId, String nodeId) {
        String key = buildKey(threadId, nodeId);
        InterruptionMetadata metadata = cache.remove(key);
        if (metadata != null) {
            log.info("取出并移除中断元数据: key={}", key);
        } else {
            log.warn("未找到中断元数据: key={}", key);
        }
        return metadata;
    }

    /**
     * 获取中断元数据（不移除）
     */
    public InterruptionMetadata get(String threadId, String nodeId) {
        String key = buildKey(threadId, nodeId);
        return cache.get(key);
    }

    /**
     * 清理过期缓存（可选）
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        log.info("清理中断元数据缓存: 共 {} 条", size);
    }
}