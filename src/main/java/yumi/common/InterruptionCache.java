package yumi.common;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中断元数据内存缓存（两级结构）
 * 一级 Key: sessionKey (threadId)
 * 二级 Key: nodeId
 * Value: InterruptionMetadata
 */
@Slf4j
@Component
public class InterruptionCache {

    private final Map<String, Map<String, InterruptionMetadata>> cache = new ConcurrentHashMap<>();

    /**
     * 存储中断元数据
     */
    public void put(String sessionKey, String nodeId, InterruptionMetadata metadata) {
        cache.computeIfAbsent(sessionKey, k -> new ConcurrentHashMap<>()).put(nodeId, metadata);
        log.info("缓存中断元数据: sessionKey={}, nodeId={}", sessionKey, nodeId);
    }

    /**
     * 获取并移除中断元数据
     */
    public InterruptionMetadata getAndRemove(String sessionKey, String nodeId) {
        Map<String, InterruptionMetadata> sessionMap = cache.get(sessionKey);
        if (sessionMap == null) {
            log.warn("未找到中断元数据: sessionKey={}, nodeId={}", sessionKey, nodeId);
            return null;
        }
        InterruptionMetadata metadata = sessionMap.remove(nodeId);
        // 如果二级 map 为空，清理一级 key
        if (sessionMap.isEmpty()) {
            cache.remove(sessionKey);
        }
        if (metadata != null) {
            log.info("取出并移除中断元数据: sessionKey={}, nodeId={}", sessionKey, nodeId);
        } else {
            log.warn("未找到中断元数据: sessionKey={}, nodeId={}", sessionKey, nodeId);
        }
        return metadata;
    }

    /**
     * 获取中断元数据（不移除）
     */
    public InterruptionMetadata get(String sessionKey, String nodeId) {
        Map<String, InterruptionMetadata> sessionMap = cache.get(sessionKey);
        if (sessionMap == null) {
            return null;
        }
        return sessionMap.get(nodeId);
    }

    /**
     * 清理指定 session 的缓存
     */
    public void clear(String sessionKey) {
        Map<String, InterruptionMetadata> removed = cache.remove(sessionKey);
        if (removed != null) {
            log.info("清理中断元数据缓存: sessionKey={}, 共 {} 条", sessionKey, removed.size());
        }
    }

    /**
     * 清理所有缓存
     */
    public void clearAll() {
        int size = cache.values().stream().mapToInt(Map::size).sum();
        cache.clear();
        log.info("清理所有中断元数据缓存: 共 {} 条", size);
    }
}