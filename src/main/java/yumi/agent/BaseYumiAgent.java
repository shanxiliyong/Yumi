package yumi.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import yumi.common.JackJsonUtil;
import yumi.common.YumiContext;
import yumi.service.IdGeneratorService;

import java.util.List;
import java.util.Optional;

import static yumi.common.ConstantUtil.BASE_THREAD_ID;
import static yumi.common.ConstantUtil.EXECUTE_ROUND;

@Slf4j
@Component
public class BaseYumiAgent implements YumiAgent {

    @Autowired
    private AgentBuilderService agentBuilderService;

    @Autowired
    private IdGeneratorService idGeneratorService;


    public String chat3(YumiContext context) {
        try {
            ReactAgent agent = agentBuilderService.buildAgent(context);
            long executeRound = idGeneratorService.nextId(context.getSessionKey());
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(context.getSessionKey())
                    .addMetadata(BASE_THREAD_ID, context.getSessionKey())
                    .addMetadata(EXECUTE_ROUND, executeRound)
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


    public String chat(YumiContext context) {
        try {
            ReactAgent agent = agentBuilderService.buildAgent(context);
            long executeRound = idGeneratorService.nextId(context.getSessionKey());
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(context.getSessionKey())
                    .addMetadata(BASE_THREAD_ID, context.getSessionKey())
                    .addMetadata(EXECUTE_ROUND, executeRound)
                    .build();

            Optional<NodeOutput> result = agent.invokeAndGetOutput(context.getRequest().getMessage(), config);
            if (!result.isPresent()) {
                log.warn("Agent 执行结果为空 threadId={}, executeRound={}", context.getSessionKey(), executeRound);
                return "处理失败: 执行结果为空";
            }
            NodeOutput output = result.get();
            // 检查中断并处理
            if (output instanceof InterruptionMetadata) {
                log.info("检测到中断，需要人工审批 threadId={}, executeRound={}", context.getSessionKey(), executeRound);
                InterruptionMetadata interruptionMetadata = (InterruptionMetadata) output;
                List<InterruptionMetadata.ToolFeedback> toolFeedbacks = interruptionMetadata.toolFeedbacks();
                log.info("中断工具调用: {}", JackJsonUtil.toJsonStr(toolFeedbacks));
                return JackJsonUtil.toJsonStr(toolFeedbacks);
            } else {
                log.info("未检测到中断，继续执行 threadId={}, executeRound={}", context.getSessionKey(), executeRound);
                var assistantMessage = agent.extractAssistantMessage(Optional.ofNullable(output.state()));
                String text = assistantMessage.getText();
                if (text != null) {
                    text = text.replace("\\n", "\n");
                }
                return text;
            }


        } catch (GraphRunnerException e) {
            log.error("Agent call error", e);
            return "处理失败: " + e.getMessage();
        }
    }

    @Override
    public Flux<String> chatStream(YumiContext context) {
        try {
            ReactAgent agent = agentBuilderService.buildAgent(context);
            long executeRound = idGeneratorService.nextId(context.getSessionKey());
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(context.getSessionKey())
                    .addMetadata(BASE_THREAD_ID, context.getSessionKey())
                    .addMetadata(EXECUTE_ROUND, executeRound)
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




}