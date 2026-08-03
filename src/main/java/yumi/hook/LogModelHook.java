package yumi.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
public class LogModelHook extends ModelHook {

    @Override
    public String getName() {
        return "custom_model_hook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        // 在模型调用前执行
        log.info("LogModelHook  准备调用模型...  state= "+System.lineSeparator()+System.lineSeparator()+" {}", state);

        // 可以修改状态
        // 例如：添加额外的上下文
        return CompletableFuture.completedFuture(Map.of("extra_context", "某些额外信息"));
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        // 在模型调用后执行
        log.info("LogModelHook  模型调用完成...  state= "+System.lineSeparator()+System.lineSeparator()+" {}", state);
        // 可以记录响应信息
        return CompletableFuture.completedFuture(Map.of());
    }


}
