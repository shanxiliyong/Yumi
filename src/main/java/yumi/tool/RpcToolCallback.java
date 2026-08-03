package yumi.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import yumi.entity.ToolEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RpcToolCallback {

    private final RpcGenericService rpcGenericService;

    public RpcToolCallback(RpcGenericService rpcGenericService) {
        this.rpcGenericService = rpcGenericService;
    }

    public ToolCallback createRpcTool(ToolEntity toolEntity) {
        RpcToolConfig config = RpcToolConfig.fromToolEntity(toolEntity);
        if (config == null) {
            throw new IllegalArgumentException("RPC Tool config parsing failed: " + toolEntity.getName());
        }

        String toolName = toolEntity.getName();
        String toolDescription = config.toToolDescription(toolName, toolEntity.getDescription());

        return FunctionToolCallback.builder(toolName, (Map<String, Object> args) -> invokeRpc(args, config))
                .description(toolDescription)
                .inputType(Map.class)
                .build();
    }

    public List<ToolCallback> createRpcTools(List<ToolEntity> toolEntities) {
        return toolEntities.stream()
                .filter(t -> "rpc".equals(t.getType()))
                .map(t -> {
                    try {
                        return createRpcTool(t);
                    } catch (Exception e) {
                        log.error("创建 RPC 工具失败: {}", t.getName(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<String, Object> invokeRpc(Map<String, Object> args, RpcToolConfig config) {
        log.info("RPC 工具被调用: {}.{}, 参数: {}，线程：{}", config.getInterfaceName(), config.getMethodName(), args, Thread.currentThread().getName());

        Map<String, Object> result = new HashMap<>();

        try {
            RpcCallRequest request = new RpcCallRequest();
            request.setInterfaceName(config.getInterfaceName());
            request.setMethodName(config.getMethodName());
            request.setParams(args != null ? args : new HashMap<>());
            request.setGroup(config.getGroup());
            request.setVersion(config.getVersion());
            request.setTimeout(config.getTimeout() != null ? config.getTimeout() : 3000);

            return rpcGenericService.invoke(request);

        } catch (Exception e) {
            log.error("RPC 工具调用异常", e);
            result.put("success", false);
            result.put("message", "调用异常: " + e.getMessage());
            return result;
        }
    }
}