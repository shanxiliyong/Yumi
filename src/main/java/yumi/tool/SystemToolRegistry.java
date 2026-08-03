package yumi.tool;

import com.alibaba.cloud.ai.graph.agent.tools.WebFetchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import yumi.entity.ToolEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Slf4j
@Component
public class SystemToolRegistry {

    public static final String DEFAULT_SUMMARIZATION_TOOL_NAME = "summarization_tool";
    public static final String DEFAULT_SHELL_TOOL_NAME = "shell_tool";
    public static final String DEFAULT_WEB_FETCH_TOOL_TOOL_NAME = "web_fetch";

    private final ChatModel chatModel;



    private final Map<String, BiFunction<ChatModel, ToolEntity, ToolCallback>> registry = new HashMap<>();

    public SystemToolRegistry(ChatModel chatModel) {
        this.chatModel = chatModel;
        register(DEFAULT_WEB_FETCH_TOOL_TOOL_NAME, this::createWebFetchTool);

    }

    private void register(String name, BiFunction<ChatModel, ToolEntity, ToolCallback> factory) {
        registry.put(name, factory);
    }

    public boolean supports(String name) {
        return name != null && registry.containsKey(name);
    }

    public ToolCallback createTool(ToolEntity entity) {
        String name = entity.getName();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("系统工具 name 不能为空");
        }
        BiFunction<ChatModel, ToolEntity, ToolCallback> factory = registry.get(name);
        if (factory == null) {
            throw new IllegalArgumentException("未注册的系统工具: " + name);
        }
        log.info("创建系统工具: name={}", name);
        return factory.apply(chatModel, entity);
    }

    private ToolCallback createWebFetchTool(ChatModel model, ToolEntity entity) {
        return WebFetchTool.builder(ChatClient.builder(model).build())
                .withName(entity.getName())
                .withDescription(entity.getDescription() != null ? entity.getDescription() : "网络搜索工具，当需要从网络搜索信息时使用")
                .build();
    }





}