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
import yumi.common.ConstantUtil;
import yumi.common.InterruptionCache;
import yumi.common.JackJsonUtil;
import yumi.common.YumiContext;
import yumi.entity.DigitalHumanEntity;
import yumi.entity.SessionEntity;
import yumi.request.ChatRequest;
import yumi.response.AgentResponse;
import yumi.service.DigitalHumanService;
import yumi.service.IdGeneratorService;
import yumi.service.SessionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static yumi.common.ConstantUtil.BASE_THREAD_ID;
import static yumi.common.ConstantUtil.EXECUTE_ROUND;
import static yumi.common.ConstantUtil.TYPE_AUDIT;
import static yumi.common.ConstantUtil.TYPE_ERROR;
import static yumi.common.ConstantUtil.TYPE_NORMAL;

@Slf4j
@Component
public class BaseYumiAgent implements YumiAgent {

    @Autowired
    private AgentBuilderService agentBuilderService;

    @Autowired
    private IdGeneratorService idGeneratorService;

    @Autowired
    private InterruptionCache interruptionCache;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private DigitalHumanService digitalHumanService;


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


    public AgentResponse chat(ChatRequest request) {


        // 普通聊天请求
        YumiContext context = new YumiContext();
        context.setRequest(request);

        SessionEntity session = sessionService.getSession(request.getSessionId());
        if (session != null && session.getDigitalHumanId() != null) {
            DigitalHumanEntity dh = digitalHumanService.getById(session.getDigitalHumanId());
            context.setDh(dh);
        }
        InterruptionMetadata approvalMetadata = null;
        // 审核请求处理
        if (ConstantUtil.TYPE_AUDIT.equals(request.getType()) && request.getNodeId() != null && request.getApproved() != null) {
            log.info("audit request: nodeId={}, approved={}", request.getNodeId(), request.getApproved());
            // 从缓存中获取中断元数据
            InterruptionMetadata preInterruptionMetadata = interruptionCache.getAndRemove(context.getSessionKey(), request.getNodeId());
            if (preInterruptionMetadata == null) {
                return AgentResponse.builder()
                        .type(ConstantUtil.TYPE_ERROR)
                        .message("未找到中断元数据")
                        .build();
            }
            if (request.getApproved()) {
                // 构建批准反馈
                InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
                        .nodeId(request.getNodeId())
                        .state(preInterruptionMetadata.state());

                // 对每个工具调用设置批准决策
                preInterruptionMetadata.toolFeedbacks().forEach(toolFeedback -> {
                    InterruptionMetadata.ToolFeedback approvedFeedback =
                            InterruptionMetadata.ToolFeedback.builder(toolFeedback)
                                    .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                                    .build();
                    feedbackBuilder.addToolFeedback(approvedFeedback);
                });
                approvalMetadata = feedbackBuilder.build();
            }

            // 普通聊天请求
            try {
                ReactAgent agent = agentBuilderService.buildAgent(context);
                long executeRound = idGeneratorService.nextId(context.getSessionKey());
                RunnableConfig.Builder builder = RunnableConfig.builder()
                        .threadId(context.getSessionKey())
                        .addMetadata(BASE_THREAD_ID, context.getSessionKey())
                        .addMetadata(EXECUTE_ROUND, executeRound);
                if (approvalMetadata != null) {
                    builder.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, approvalMetadata);
                }

                Optional<NodeOutput> result = agent.invokeAndGetOutput(request.getMessage(), builder.build());
                if (!result.isPresent()) {
                    log.warn("Agent 执行结果为空 threadId={}, executeRound={}", context.getSessionKey(), executeRound);
                    return AgentResponse.builder()
                            .type(TYPE_ERROR)
                            .message("执行结果为空")
                            .build();
                }
                NodeOutput output = result.get();
                // 检查中断并处理
                if (output instanceof InterruptionMetadata) {
                    log.info("检测到中断，需要人工审批 threadId={}, executeRound={}", context.getSessionKey(), executeRound);
                    InterruptionMetadata interruptionMetadata = (InterruptionMetadata) output;
                    List<InterruptionMetadata.ToolFeedback> toolFeedbacks = interruptionMetadata.toolFeedbacks();
                    log.info("中断工具调用: {}", JackJsonUtil.toJsonStr(toolFeedbacks));

                    // 存入内存缓存
                    interruptionCache.put(context.getSessionKey(), interruptionMetadata.node(), interruptionMetadata);

                    Map<String, Object> extraInfo = new HashMap<>();
                    extraInfo.put("nodeId", interruptionMetadata.node());

                    return AgentResponse.builder()
                            .type(TYPE_AUDIT)
                            .confirmInfo(toolFeedbacks)
                            .extraInfo(extraInfo)
                            .build();
                } else {
                    log.info("未检测到中断，继续执行 threadId={}, executeRound={}", context.getSessionKey(), executeRound);
                    var assistantMessage = agent.extractAssistantMessage(Optional.ofNullable(output.state()));
                    String text = assistantMessage.getText();
                    if (text != null) {
                        text = text.replace("\\n", "\n");
                    }

                    return AgentResponse.builder()
                            .type(TYPE_NORMAL)
                            .message(text)
                            .build();
                }


            } catch (GraphRunnerException e) {
                log.error("Agent call error", e);
                return AgentResponse.builder()
                        .type(TYPE_ERROR)
                        .message("处理失败: " + e.getMessage())
                        .build();
            }
        }

        @Override
        public Flux<String> chatStream (YumiContext context){
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