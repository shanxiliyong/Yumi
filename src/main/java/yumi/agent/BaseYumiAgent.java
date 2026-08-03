package yumi.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent2;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import yumi.common.YumiContext;

@Slf4j
@Component
public class BaseYumiAgent implements YumiAgent {

    @Autowired
    private AgentBuilderService agentBuilderService;


    @Override
    public String chat(YumiContext context) {
        try {
            ReactAgent2 agent = agentBuilderService.buildAgent(context);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(context.getSessionKey())
                    .build();
            var result = agent.call(context.getRequest().getMessage(), config);
            String text = result.getText();
            if (text != null) {
                text = text.replace("\\n", "\n");
            }
            return text;
        } catch (GraphRunnerException e) {
            log.error("Agent call error", e);
            return "处理失败: " + e.getMessage();
        }
    }

    @Override
    public Flux<String> chatStream(YumiContext context) {
        try {
            ReactAgent2 agent = agentBuilderService.buildAgent(context);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(context.getSessionKey())
                    .build();

            return agent.stream(context.getRequest().getMessage(), config)
                    .filter(nodeOutput -> nodeOutput instanceof StreamingOutput streamingOutput
                            && (streamingOutput.getOutputType() == OutputType.AGENT_MODEL_STREAMING
                            || streamingOutput.getOutputType() == OutputType.AGENT_MODEL_FINISHED))
                    .map(nodeOutput -> {
                        StreamingOutput streamingOutput = (StreamingOutput) nodeOutput;
                        String text = streamingOutput.message() != null ? streamingOutput.message().getText() : "";
                        return text.replaceAll("data:", "");
                    })
                    .filter(text -> text != null && !text.isEmpty())
                    .buffer(3)
                    .map(chunks -> {
                        String joined = String.join("", chunks);
                        return joined.replace("\\n", "\n");
                    })
                    .doOnNext(merged -> log.info("chatStream merged: {}", merged))
                    .onErrorResume(error -> {
                        String errorMsg = "[错误] " + error.getMessage();
                        return Flux.just(errorMsg);
                    });
        } catch (Exception e) {
            String errorMsg = "[错误] " + e.getMessage();
            return Flux.just(errorMsg);
        }
    }

    private String getKey(String sessionKey) {
        return sessionKey != null ? sessionKey : "default";
    }


}