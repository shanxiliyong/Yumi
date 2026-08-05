package yumi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yumi.entity.CheckpointEntity;
import yumi.mapper.CheckpointMapper;

import java.util.*;

@Slf4j
@Service
public class CheckpointService {

    @Autowired
    private CheckpointMapper checkpointMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<CheckpointEntity> getAllByThreadId(String threadId) {
        List<CheckpointEntity> list = checkpointMapper.selectByThreadIdOrderBySeq(threadId);
        processCheckpoints(list);
        return list;
    }

    private void processCheckpoints(List<CheckpointEntity> list) {
        if (list == null || list.isEmpty()) return;
        
        for (int i = 0; i < list.size(); i++) {
            CheckpointEntity checkpoint = list.get(i);
            String stateDataJson = checkpoint.getStateDataJson();
            String nodeId = checkpoint.getNodeId();
            
            // 解析消息内容
            String messageContent = extractMessageContent(stateDataJson);
            checkpoint.setMessageContent(messageContent);
            
            // 解析快照状态
            String snapshotStatus = extractSnapshotStatus(nodeId, stateDataJson, i, list);
            checkpoint.setSnapshotStatus(snapshotStatus);
        }
    }

    private String extractMessageContent(String stateDataJson) {
        if (stateDataJson == null || stateDataJson.trim().isEmpty()) return "";
        
        try {
            JsonNode root = objectMapper.readTree(stateDataJson);
            JsonNode messagesNode = findMessagesNodeRecursive(root);
            
            if (messagesNode != null && messagesNode.isArray() && messagesNode.size() > 0) {
                JsonNode lastMsg = messagesNode.get(messagesNode.size() - 1);
                String type = extractMessageType(lastMsg);
                String content = extractMessageText(lastMsg);
                
                if (type != null && content != null) {
                    return type.toUpperCase() + "：" + content;
                }
            }
        } catch (Exception e) {
            log.warn("解析消息内容失败", e);
        }
        return "";
    }

    private String extractMessageType(JsonNode msgNode) {
        if (msgNode == null || msgNode.isNull()) return null;
        
        if (msgNode.has("messageType") && msgNode.get("messageType").isTextual()) {
            return msgNode.get("messageType").asText();
        }
        if (msgNode.has("type") && msgNode.get("type").isTextual()) {
            return msgNode.get("type").asText();
        }
        if (msgNode.has("role") && msgNode.get("role").isTextual()) {
            return msgNode.get("role").asText();
        }
        return null;
    }

    private String extractMessageText(JsonNode msgNode) {
        if (msgNode == null || msgNode.isNull()) return null;
        
        if (msgNode.has("text") && msgNode.get("text").isTextual()) {
            return msgNode.get("text").asText();
        }
        if (msgNode.has("content") && msgNode.get("content").isTextual()) {
            return msgNode.get("content").asText();
        }
        return null;
    }

    private String extractSnapshotStatus(String nodeId, String stateDataJson, int currentIndex, List<CheckpointEntity> list) {
        if (!"_AGENT_HOOK_Summarization.beforeModel".equals(nodeId)) {
            return "";
        }
        
        if (currentIndex > 0) {
            CheckpointEntity prevCheckpoint = list.get(currentIndex - 1);
            if (stateDataJson != null && stateDataJson.equals(prevCheckpoint.getStateDataJson())) {
                return "未压缩";
            }
        }
        return "压缩";
    }

    private JsonNode findMessagesNodeRecursive(JsonNode node) {
        if (node == null || node.isNull()) return null;

        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode result = findMessagesNodeRecursive(child);
                if (result != null) return result;
            }
        } else if (node.isObject()) {
            if (node.has("messages") && node.get("messages").isArray() && node.get("messages").size() > 0) {
                return node.get("messages");
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode result = findMessagesNodeRecursive(entry.getValue());
                if (result != null) return result;
            }
        }
        return null;
    }

    public CheckpointEntity getLatestByThreadId(String threadId) {
        return checkpointMapper.selectLatestByThreadId(threadId);
    }

    /**
     * 从 checkpoint 的 state_data_json（纯 JSON 格式）中解析消息历史
     * state_data 是 Base64 二进制序列化结果，state_data_json 是可读 JSON
     */
    public List<Map<String, Object>> getMessagesFromCheckpoint(String threadId) {
        List<Map<String, Object>> messages = new ArrayList<>();
        try {
            CheckpointEntity latest = checkpointMapper.selectLatestByThreadId(threadId);
            if (latest == null) {
                log.debug("未找到 threadId={} 的 checkpoint", threadId);
                return messages;
            }

            String stateJson = latest.getStateDataJson();
            if (stateJson == null || stateJson.trim().isEmpty()) {
                log.debug("threadId={} 的 state_data_json 为空，尝试使用 state_data", threadId);
                stateJson = latest.getStateData();
            }
            if (stateJson == null || stateJson.trim().isEmpty()) {
                return messages;
            }

            JsonNode root = objectMapper.readTree(stateJson);
            log.debug("threadId={}, state_data_json 顶级字段: {}", threadId, getFieldNames(root));

            List<Map<String, Object>> extracted = extractMessagesFromJson(root);
            if (!extracted.isEmpty()) {
                messages.addAll(extracted);
            }
        } catch (Exception e) {
            log.error("从 checkpoint 解析消息失败, threadId={}", threadId, e);
        }
        return messages;
    }

    private String getFieldNames(JsonNode node) {
        if (node == null || !node.isObject()) return "非对象";
        List<String> names = new ArrayList<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            JsonNode child = node.get(name);
            String typeInfo = child.isArray() ? "(array," + child.size() + ")"
                    : child.isObject() ? "(object)"
                    : child.isTextual() ? "(text:" + child.asText().replace("\n", "\\n").substring(0, Math.min(50, child.asText().length())) + ")"
                    : "(" + child.getNodeType() + ")";
            names.add(name + typeInfo);
        }
        return String.join(", ", names);
    }

    private List<Map<String, Object>> extractMessagesFromJson(JsonNode root) {
        List<Map<String, Object>> messages = new ArrayList<>();

        if (root == null || root.isNull()) {
            return messages;
        }

        if (root.has("messages") && root.get("messages").isArray() && root.get("messages").size() > 0) {
            messages.addAll(parseMessagesNode(root.get("messages")));
            if (!messages.isEmpty()) return messages;
        }

        if (root.has("channel_map")) {
            JsonNode channelMap = root.get("channel_map");
            if (channelMap.has("messages") && channelMap.get("messages").isArray()) {
                messages.addAll(parseMessagesNode(channelMap.get("messages")));
                if (!messages.isEmpty()) return messages;
            }
        }

        JsonNode messagesNode = findMessagesNodeRecursive(root);
        if (messagesNode != null && messagesNode.isArray() && messagesNode.size() > 0) {
            messages.addAll(parseMessagesNode(messagesNode));
        }

        return messages;
    }

    private List<Map<String, Object>> parseMessagesNode(JsonNode messagesNode) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (messagesNode == null || !messagesNode.isArray()) {
            return messages;
        }

        for (JsonNode msgNode : messagesNode) {
            try {
                Map<String, Object> parsed = parseSingleMessage(msgNode);
                if (parsed != null) {
                    messages.add(parsed);
                }
            } catch (Exception e) {
                log.warn("解析单条消息失败: {}", msgNode);
            }
        }
        return messages;
    }

    private Map<String, Object> parseSingleMessage(JsonNode msgNode) {
        if (msgNode == null || msgNode.isNull()) return null;
        if (msgNode.isTextual()) return null;

        String type = null;
        String content = null;

        if (msgNode.has("type") && msgNode.get("type").isTextual()) {
            String t = msgNode.get("type").asText().toLowerCase();
            if (t.contains("user")) type = "user";
            else if (t.contains("assistant") || t.contains("ai") || t.contains("bot")) type = "bot";
            else if (t.contains("system")) type = "system";
            else type = "bot";
        }
        if (type == null && msgNode.has("role") && msgNode.get("role").isTextual()) {
            String role = msgNode.get("role").asText().toLowerCase();
            if (role.contains("user")) type = "user";
            else if (role.contains("assistant") || role.contains("ai")) type = "bot";
            else if (role.contains("system")) type = "system";
        }
        if (type == null && msgNode.has("messageType") && msgNode.get("messageType").isTextual()) {
            String mt = msgNode.get("messageType").asText().toLowerCase();
            if (mt.contains("user")) type = "user";
            else if (mt.contains("assistant") || mt.contains("ai")) type = "bot";
            else if (mt.contains("system")) type = "system";
        }
        if (type == null && msgNode.has("message") && msgNode.get("message").isObject()) {
            JsonNode inner = msgNode.get("message");
            if (inner.has("type") && inner.get("type").isTextual()) {
                String t = inner.get("type").asText().toLowerCase();
                if (t.contains("user")) type = "user";
                else if (t.contains("assistant") || t.contains("ai")) type = "bot";
            }
        }

        if (msgNode.has("content") && !msgNode.get("content").isNull()) {
            JsonNode c = msgNode.get("content");
            if (c.isTextual()) {
                content = c.asText();
            } else if (c.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : c) {
                    if (item.has("text")) {
                        sb.append(item.get("text").asText());
                    } else if (item.isTextual()) {
                        sb.append(item.asText());
                    }
                }
                content = sb.toString();
            } else if (c.isObject() && c.has("text")) {
                content = c.get("text").asText();
            }
        }
        if ((content == null || content.isEmpty()) && msgNode.has("text") && msgNode.get("text").isTextual()) {
            content = msgNode.get("text").asText();
        }
        if ((content == null || content.isEmpty()) && msgNode.has("message") && msgNode.get("message").isObject()) {
            JsonNode inner = msgNode.get("message");
            if (inner.has("content") && inner.get("content").isTextual()) {
                content = inner.get("content").asText();
            } else if (inner.has("text") && inner.get("text").isTextual()) {
                content = inner.get("text").asText();
            }
        }
        if ((content == null || content.isEmpty()) && msgNode.has("agentId") && msgNode.has("graphState")) {
            JsonNode graphState = msgNode.get("graphState");
            JsonNode innerMsg = findMessagesNodeRecursive(graphState);
            if (innerMsg != null && innerMsg.isArray() && innerMsg.size() > 0) {
                JsonNode lastMsg = innerMsg.get(innerMsg.size() - 1);
                if (lastMsg.has("content") && lastMsg.get("content").isTextual()) {
                    content = lastMsg.get("content").asText();
                }
            }
        }

        if (content != null && !content.trim().isEmpty()) {
            if ("system".equals(type)) {
                return null;
            }
            if (type == null) {
                type = "bot";
            }
            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.put("content", content.trim());
            return message;
        }
        return null;
    }
}