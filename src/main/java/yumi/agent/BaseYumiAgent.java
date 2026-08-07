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



    /**
     * 同步对话方法
     * 处理用户的同步聊天请求，包括构建上下文、处理审核请求、执行Agent并处理输出
     *
     * @param request 聊天请求对象，包含消息、会话ID等信息
     * @return Agent响应对象，包含消息内容、类型等信息
     */
    public AgentResponse chat(ChatRequest request) {
        try {
            // 1. 构建上下文：加载数字人配置、生成执行轮次等
            YumiContext context = buildContext(request);

            // 2. 处理审核请求：如果是审核请求，返回审批元数据
            InterruptionMetadata approvalMetadata = handleAuditRequest(request, context);

            // 3. 构建 Agent 和配置：根据上下文创建Agent实例和执行配置
            ReactAgent agent = agentBuilderService.buildAgent(context);
            RunnableConfig config = buildRunnableConfig(context, approvalMetadata);

            // 4. 执行 Agent：调用Agent处理用户消息
            Optional<NodeOutput> result = agent.invokeAndGetOutput(request.getMessage(), config);

            // 5. 处理输出：根据输出类型（中断/正常）返回不同响应
            return handleAgentOutput(agent, result, context);

        } catch (GraphRunnerException e) {
            log.error("Agent call error", e);
            return AgentResponse.builder().type(TYPE_ERROR).message("处理失败: " + e.getMessage()).build();
        }
    }

    /**
     * 构建执行上下文
     * 根据聊天请求创建YumiContext对象，加载数字人配置并生成执行轮次
     *
     * @param request 聊天请求对象
     * @return 构建完成的YumiContext对象
     */
    private YumiContext buildContext(ChatRequest request) {
        YumiContext context = new YumiContext();
        // 设置请求对象到上下文
        context.setRequest(request);

        // 根据会话ID获取会话信息，并加载对应的数字人配置
        SessionEntity session = sessionService.getSession(request.getSessionId());
        if (session != null && session.getDigitalHumanId() != null) {
            DigitalHumanEntity dh = digitalHumanService.getById(session.getDigitalHumanId());
            context.setDh(dh);
        }

        // 生成执行轮次：用于追踪同一会话中的多次执行
        context.setExecuteRound(idGeneratorService.nextId(context.getSessionKey()));

        return context;
    }

    /**
     * 处理审核请求，返回审批元数据（非审核请求返回 null）
     * 当用户提交审核请求时，从缓存中获取中断元数据，并根据用户的审批决定构建反馈
     *
     * @param request 聊天请求对象，包含审核相关信息
     * @param context 执行上下文对象
     * @return 审批元数据对象，非审核请求或用户拒绝时返回null
     * @throws IllegalStateException 当找不到中断元数据时抛出
     */
    private InterruptionMetadata handleAuditRequest(ChatRequest request, YumiContext context) {
        // 判断是否为审核请求：需要type为AUDIT，且nodeId和approved不为空
        if (!ConstantUtil.TYPE_AUDIT.equals(request.getType())
                || request.getNodeId() == null
                || request.getApproved() == null) {
            return null;
        }

        log.info("audit request: nodeId={}, approved={}", request.getNodeId(), request.getApproved());

        // 从缓存中获取并移除中断元数据（一次性使用）
        InterruptionMetadata preMetadata = interruptionCache.getAndRemove(context.getSessionKey(), request.getNodeId());
        if (preMetadata == null) {
            throw new IllegalStateException("未找到中断元数据");
        }

        // 如果用户拒绝执行，则返回null，不需要approvalMetadata
        if (!request.getApproved()) {
            return null; // 取消执行，不需要 approvalMetadata
        }

        // 构建批准反馈：将工具反馈标记为已批准
        InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
                .nodeId(request.getNodeId())
                .state(preMetadata.state());

        // 遍历所有工具反馈，标记为APPROVED状态
        preMetadata.toolFeedbacks().forEach(toolFeedback -> {
            InterruptionMetadata.ToolFeedback approvedFeedback =
                    InterruptionMetadata.ToolFeedback.builder(toolFeedback)
                            .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                            .build();
            feedbackBuilder.addToolFeedback(approvedFeedback);
        });

        return feedbackBuilder.build();
    }

    /**
     * 构建 RunnableConfig
     * 创建Agent执行所需的配置对象，包括线程ID、执行轮次、审批元数据等
     *
     * @param context 执行上下文对象
     * @param approvalMetadata 审批元数据，可为null
     * @return 构建完成的RunnableConfig对象
     */
    private RunnableConfig buildRunnableConfig(YumiContext context, InterruptionMetadata approvalMetadata) {
        // 构建基础配置：设置线程ID和执行轮次元数据
        RunnableConfig.Builder builder = RunnableConfig.builder()
                .threadId(context.getSessionKey())
                .addMetadata(BASE_THREAD_ID, context.getSessionKey())
                .addMetadata(EXECUTE_ROUND, context.getExecuteRound());

        // 如果有审批元数据，添加到配置中
        if (approvalMetadata != null) {
            builder.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, approvalMetadata);
        }

        return builder.build();
    }

    /**
     * 处理 Agent 输出结果
     * 根据Agent执行结果的不同类型（中断/正常）构建相应的响应对象
     * 中断场景需要缓存元数据并返回审批信息，正常场景返回Agent回复
     *
     * @param agent ReactAgent实例
     * @param result Agent执行结果的Optional包装
     * @param context 执行上下文对象
     * @return Agent响应对象
     */
    private AgentResponse handleAgentOutput(ReactAgent agent, Optional<NodeOutput> result, YumiContext context) {
        // 检查执行结果是否为空
        if (!result.isPresent()) {
            log.warn("Agent 执行结果为空 threadId={}, executeRound={}", context.getSessionKey(), context.getExecuteRound());
            return AgentResponse.builder().type(TYPE_ERROR).message("执行结果为空").build();
        }

        NodeOutput output = result.get();
        // 中断场景：需要人工审批（如执行shell命令前的确认）
        if (output instanceof InterruptionMetadata interruptionMetadata) {
            log.info("检测到中断，需要人工审批 threadId={}, executeRound={}", context.getSessionKey(), context.getExecuteRound());
            List<InterruptionMetadata.ToolFeedback> toolFeedbacks = interruptionMetadata.toolFeedbacks();
            log.info("中断工具调用: {}", JackJsonUtil.toJsonStr(toolFeedbacks));

            // 将中断元数据存入内存缓存，等待用户审批
            interruptionCache.put(context.getSessionKey(), interruptionMetadata.node(), interruptionMetadata);

            // 构建额外信息，包含节点ID用于后续审批
            Map<String, Object> extraInfo = new HashMap<>();
            extraInfo.put("nodeId", interruptionMetadata.node());

            return AgentResponse.builder()
                    .type(TYPE_AUDIT)
                    .confirmInfo(toolFeedbacks)
                    .extraInfo(extraInfo)
                    .build();
        }

        // 正常场景：Agent执行完成，提取回复消息
        log.info("未检测到中断，继续执行 threadId={}, executeRound={}", context.getSessionKey(), context.getExecuteRound());
        var assistantMessage = agent.extractAssistantMessage(Optional.ofNullable(output.state()));
        String text = assistantMessage.getText();
        // 处理换行符转义
        if (text != null) {
            text = text.replace("\\n", "\n");
        }

        return AgentResponse.builder().type(TYPE_NORMAL).message(text).build();
    }

    /**
     * 流式对话方法
     * 处理用户的流式聊天请求，返回Flux对象用于实时推送Agent回复
     * 支持流式输出过滤、文本合并、错误处理等功能
     *
     * @param context 执行上下文对象
     * @return Flux流式字符串输出
     */
    @Override
    public Flux<String> chatStream(YumiContext context) {
        try {
            // 构建Agent实例
            ReactAgent agent = agentBuilderService.buildAgent(context);
            // 生成执行轮次
            long executeRound = idGeneratorService.nextId(context.getSessionKey());
            // 构建执行配置
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(context.getSessionKey())
                    .addMetadata(BASE_THREAD_ID, context.getSessionKey())
                    .addMetadata(EXECUTE_ROUND, executeRound)
                    .build();

            // 流式调用Agent，并进行一系列处理
            return agent.stream(context.getRequest().getMessage(), config)
                    // 过滤：只保留AGENT_MODEL_STREAMING和AGENT_MODEL_FINISHED类型的输出
                    .filter(nodeOutput -> nodeOutput instanceof StreamingOutput streamingOutput
                            && (streamingOutput.getOutputType() == OutputType.AGENT_MODEL_STREAMING
                            || streamingOutput.getOutputType() == OutputType.AGENT_MODEL_FINISHED))
                    // 提取文本消息，并移除"data:"前缀
                    .map(nodeOutput -> {
                        StreamingOutput streamingOutput = (StreamingOutput) nodeOutput;
                        String text = streamingOutput.message() != null ? streamingOutput.message().getText() : "";
                        return text.replaceAll("data:", "");
                    })
                    // 过滤空文本
                    .filter(text -> text != null && !text.isEmpty())
                    // 每3个文本块合并为一组，减少输出频率
                    .buffer(3)
                    // 合并文本块，并处理换行符转义
                    .map(chunks -> {
                        String joined = String.join("", chunks);
                        return joined.replace("\\n", "\n");
                    })
                    // 记录合并后的输出日志
                    .doOnNext(merged -> log.info("chatStream merged: {}", merged))
                    // 错误处理：捕获异常并返回错误消息
                    .onErrorResume(error -> {
                        String errorMsg = "[错误] " + error.getMessage();
                        return Flux.just(errorMsg);
                    });
        } catch (Exception e) {
            // 捕获构建阶段的异常，返回错误消息
            String errorMsg = "[错误] " + e.getMessage();
            return Flux.just(errorMsg);
        }
    }


}