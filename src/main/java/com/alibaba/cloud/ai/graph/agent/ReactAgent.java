/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.graph.agent;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.SubGraphNode;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.exception.AgentException;
import com.alibaba.cloud.ai.graph.agent.factory.AgentBuilderFactory;
import com.alibaba.cloud.ai.graph.agent.factory.DefaultAgentBuilderFactory;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.InstructionAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.InterruptionHook;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.ToolInjection;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.StreamingModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.alibaba.cloud.ai.graph.agent.node.AgentLlmNode;
import com.alibaba.cloud.ai.graph.agent.node.AgentToolNode;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.internal.node.Node;
import com.alibaba.cloud.ai.graph.internal.node.ResumableSubGraphAction;
import com.alibaba.cloud.ai.graph.serializer.AgentInstructionMessage;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.utils.TypeRef;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;

import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.graph.RunnableConfig.AGENT_MODEL_NAME;
import static com.alibaba.cloud.ai.graph.RunnableConfig.AGENT_TOOL_NAME;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig.node_async;
import static com.alibaba.cloud.ai.graph.agent.hook.InterruptionHook.INTERRUPTION_FEEDBACK_KEY;
import static com.alibaba.cloud.ai.graph.internal.node.ResumableSubGraphAction.resumeSubGraphId;
import static com.alibaba.cloud.ai.graph.internal.node.ResumableSubGraphAction.subGraphId;
import static java.lang.String.format;


public class ReactAgent extends BaseAgent {
    /** 日志记录器，用于记录 ReactAgent 运行时的日志信息 */
    Logger logger = LoggerFactory.getLogger(ReactAgent.class);

    /** 线程 ID 与状态映射表，用于维护不同线程的执行状态 */
    private final ConcurrentMap<String, Map<String, Object>> threadIdStateMap;

    /** Agent LLM 节点，负责调用大语言模型进行推理 */
    private final AgentLlmNode llmNode;

    /** Agent 工具节点，负责执行工具调用 */
    private final AgentToolNode toolNode;

    /** Hook 列表，用于在 Agent 生命周期的不同阶段插入自定义逻辑 */
    private List<? extends Hook> hooks;

    /** 模型拦截器列表，用于在 LLM 调用前后进行拦截处理 */
    private List<ModelInterceptor> modelInterceptors;

    /** 工具拦截器列表，用于在工具调用前后进行拦截处理 */
    private List<ToolInterceptor> toolInterceptors;

    /** 流式模型拦截器列表，用于在流式输出时进行拦截处理 */
    private List<StreamingModelInterceptor> streamingInterceptors;

    /** Agent 指令，定义 Agent 的行为和角色 */
    private String instruction;

    /** 状态序列化器，用于状态的序列化、反序列化和工厂创建 */
    private StateSerializer stateSerializer;

    /** 是否包含工具的标志，用于判断是否需要注册 Tool 节点 */
    private final Boolean hasTools;

    public ReactAgent(AgentLlmNode llmNode, AgentToolNode toolNode, CompileConfig compileConfig, Builder builder) {
        super(builder.name, builder.description, builder.includeContents, builder.returnReasoningContents, builder.outputKey, builder.outputKeyStrategy);
        this.threadIdStateMap = new ConcurrentHashMap<>();

        this.instruction = builder.instruction;
        this.llmNode = llmNode;
        this.toolNode = toolNode;
        this.compileConfig = compileConfig;
        this.hooks = builder.hooks;
        this.modelInterceptors = builder.modelInterceptors;
        this.toolInterceptors = builder.toolInterceptors;
        this.streamingInterceptors = builder.streamingInterceptors;
        this.includeContents = builder.includeContents;
        this.inputSchema = builder.inputSchema;
        this.inputType = builder.inputType;
        this.outputSchema = builder.outputSchema;
        this.outputType = builder.outputType;

        // Set state serializer from builder, or use default
        // Default to Jackson serializer for better compatibility and features
        this.stateSerializer = Objects.requireNonNullElseGet(builder.stateSerializer, () -> new SpringAIJacksonStateSerializer(OverAllState::new));

        // Set executor configuration from builder
        this.executor = builder.executor;

        // Set interceptors to nodes
        // Collect interceptors from hooks and merge with current interceptors
        List<ModelInterceptor> mergedModelInterceptors = collectAndMergeModelInterceptors();
        List<ToolInterceptor> mergedToolInterceptors = collectAndMergeToolInterceptors();

        if (mergedModelInterceptors != null && !mergedModelInterceptors.isEmpty()) {
            this.llmNode.setModelInterceptors(mergedModelInterceptors);
        }
        if (mergedToolInterceptors != null && !mergedToolInterceptors.isEmpty()) {
            this.toolNode.setToolInterceptors(mergedToolInterceptors);
        }
        if (this.streamingInterceptors != null && !this.streamingInterceptors.isEmpty()) {
            this.llmNode.setStreamingInterceptors(this.streamingInterceptors);
        }

        // Set tools flag if tool interceptors are present.
        hasTools = toolNode.getToolCallbacks() != null && !toolNode.getToolCallbacks().isEmpty();
    }

    public static Builder builder() {
        return new DefaultAgentBuilderFactory().builder();
    }

    public static Builder builder(AgentBuilderFactory agentBuilderFactory) {
        return agentBuilderFactory.builder();
    }

    public AssistantMessage call(String message) throws GraphRunnerException {
        return doMessageInvoke(message, null);
    }

    public AssistantMessage call(String message, RunnableConfig config) throws GraphRunnerException {
        return doMessageInvoke(message, config);
    }

    public AssistantMessage call(UserMessage message) throws GraphRunnerException {
        return doMessageInvoke(message, null);
    }

    public AssistantMessage call(UserMessage message, RunnableConfig config) throws GraphRunnerException {
        return doMessageInvoke(message, config);
    }

    public AssistantMessage call(List<Message> messages) throws GraphRunnerException {
        return doMessageInvoke(messages, null);
    }

    public AssistantMessage call(List<Message> messages, RunnableConfig config) throws GraphRunnerException {
        return doMessageInvoke(messages, config);
    }

    /**
     * Calls the agent with the given inputs map and returns the assistant message.
     * <p>
     * When you need to pass additional parameters beyond {@code messages} and {@code input},
     * use this overload.
     * <p>
     * Reserved keys: {@code messages} and {@code input} are used as question/input for the
     * agent. Other keys can be arbitrary and are passed as graph state, e.g. for prompt
     * placeholders or any other state values.
     *
     * @param inputs the input map (reserved: messages, input; other keys as state)
     * @return the assistant message response
     * @throws GraphRunnerException if the graph execution fails
     */
    public AssistantMessage call(Map<String, Object> inputs) throws GraphRunnerException {
        return doMessageInvoke(inputs, null);
    }

    /**
     * Calls the agent with the given inputs map and runtime config, returns the assistant message.
     * <p>
     * When you need to pass additional parameters beyond {@code messages} and {@code input},
     * use this overload.
     * <p>
     * Reserved keys: {@code messages} and {@code input} are used as question/input for the
     * agent. Other keys can be arbitrary and are passed as graph state, e.g. for prompt
     * placeholders or any other state values.
     *
     * @param inputs the input map (reserved: messages, input; other keys as state)
     * @param config runtime configuration controlling execution behavior
     * @return the assistant message response
     * @throws GraphRunnerException if the graph execution fails
     */
    public AssistantMessage call(Map<String, Object> inputs, RunnableConfig config) throws GraphRunnerException {
        return doMessageInvoke(inputs, config);
    }

    public void interrupt(RunnableConfig config) {
        updateAgentState(List.of(), config);
    }

    public void interrupt(List<Message> messages, RunnableConfig config) {
        updateAgentState(messages, config);
    }

    public void interrupt(String userMessage, RunnableConfig config) {
        updateAgentState(List.of(UserMessage.builder().text(userMessage).build()), config);
    }

    /**
     * Updates the agent thread state with interruption feedback.
     * This method is thread-safe and can be called concurrently with apply() in InterruptionHook.
     *
     * Thread-safety guarantees:
     * - threadIdStateMap is a ConcurrentHashMap, ensuring thread-safe access
     * - computeIfAbsent ensures atomic creation of the inner map if it doesn't exist
     * - The inner map is always a ConcurrentHashMap, ensuring thread-safe put() operations
     *
     * Concurrency behavior:
     * - If called before apply() processes feedback: the new value will be processed
     * - If called after apply() removes feedback: the new value will be set for next iteration
     * - If called concurrently with apply(): the atomic operations ensure no data loss
     */
    public void updateAgentState(Object state, RunnableConfig config) {
        String threadId = config.threadId().orElseThrow(() -> new IllegalArgumentException("threadId must be provided in RunnableConfig for interruption."));
        // computeIfAbsent is atomic - ensures thread-safe creation of inner map
        // The inner map is always ConcurrentHashMap, ensuring thread-safe put() operations
        Map<String, Object> stateStatus = threadIdStateMap.computeIfAbsent(threadId, k -> new ConcurrentHashMap<>());
        stateStatus.put(INTERRUPTION_FEEDBACK_KEY, state);
    }

    private AssistantMessage doMessageInvoke(Object message, RunnableConfig config) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return extractAssistantMessage(doInvoke(inputs, config));
    }

    private AssistantMessage doMessageInvoke(Map<String, Object> inputs, RunnableConfig config) throws GraphRunnerException {
        return extractAssistantMessage(doInvoke(inputs, config));
    }

    public AssistantMessage extractAssistantMessage(Optional<OverAllState> state) {
        if (StringUtils.hasLength(outputKey)) {

            return state.flatMap(s -> s.value(outputKey))
                    .map(msg -> (AssistantMessage) msg)
                    .orElseThrow(() -> new IllegalStateException("Output key " + outputKey + " not found in agent state"));
        }

        // Add a validation instance when performing message conversion to
        // avoid potential type conversion exceptions.
        return state.flatMap(s -> s.value("messages"))
                .stream()
                .flatMap(messageList -> ((List<?>) messageList).stream()
                        .filter(msg -> msg instanceof AssistantMessage)
                        .map(msg -> (AssistantMessage) msg))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AgentException("No AssistantMessage found in 'messages' state"));
    }

    public StateGraph getStateGraph() {
        return getGraph();
    }

    public CompiledGraph getCompiledGraph() {
        return compiledGraph;
    }

    @Override
    public Node asNode(boolean includeContents, boolean returnReasoningContents) {
        if (this.compiledGraph == null) {
            this.compiledGraph = getAndCompileGraph();
        }
        return new AgentSubGraphNode(this.name, includeContents, returnReasoningContents, this.compiledGraph, this.instruction);
    }

    /**
     * 初始化并构建 ReAct Agent 的状态图
     * <p>
     * 该方法实现了 ReAct（Reasoning + Acting）模式的图构建流程，主要包括：
     * 1. 注入默认的 InstructionAgentHook，处理指令逻辑
     * 2. 验证 Hook 的唯一性，设置 Agent 名称和引用
     * 3. 创建 StateGraph，注册 Agent Model 节点和 Tool 节点
     * 4. 为需要工具的 Hook 注入工具依赖
     * 5. 按 HookPosition 分类 Hook（BEFORE_AGENT、AFTER_AGENT、BEFORE_MODEL、AFTER_MODEL）
     * 6. 为各类 Hook 创建对应的图节点
     * 7. 确定节点执行流程（入口节点、循环入口、循环出口、退出节点）
     * 8. 建立节点之间的边连接关系
     *
     * @return 构建完成的 StateGraph 实例
     * @throws GraphStateException 如果图构建过程中出现状态异常
     */
    @Override
    protected StateGraph initGraph() throws GraphStateException {

        // 初始化 hooks 列表，如果为 null 则创建空列表
        if (hooks == null) {
            hooks = new ArrayList<>();
        }

        // 始终注入默认的 InstructionAgentHook，确保指令在 beforeAgent 阶段被处理
        List<Hook> effectiveHooks = new ArrayList<>();
        effectiveHooks.add(InstructionAgentHook.create());
        effectiveHooks.addAll(hooks);

        // 验证 Hook 的唯一性，不允许重复的 Hook 实例
        Set<String> hookNames = new HashSet<>();
        for (Hook hook : effectiveHooks) {
            if (!hookNames.add(Hook.getFullHookName(hook))) {
                throw new IllegalArgumentException("Duplicate hook instances found");
            }

            // 为每个 Hook 节点设置 Agent 名称和引用
            hook.setAgentName(this.name);
            hook.setAgent(this);
        }

        // 创建 StateGraph，使用消息键策略工厂和状态序列化器
        StateGraph graph = new StateGraph(name, buildMessagesKeyStrategyFactory(effectiveHooks), stateSerializer);

        // 注册 Agent Model 节点，用于调用 LLM 进行推理
        graph.addNode(AGENT_MODEL_NAME, node_async(this.llmNode));
        // 如果有工具，注册 Tool 节点，用于执行工具调用
        if (hasTools) {
            graph.addNode(AGENT_TOOL_NAME, node_async(this.toolNode));
        }

        // 为需要工具的 Hook 注入工具依赖，支持 Hook 在初始化/清理阶段使用工具
        setupToolsForHooks(effectiveHooks, toolNode);

        // 按 HookPosition 分类 Hook，确定它们在 Agent 生命周期中的执行位置
        List<Hook> beforeAgentHooks = filterHooksByPosition(effectiveHooks, HookPosition.BEFORE_AGENT);
        List<Hook> afterAgentHooks = filterHooksByPosition(effectiveHooks, HookPosition.AFTER_AGENT);
        List<Hook> beforeModelHooks = filterHooksByPosition(effectiveHooks, HookPosition.BEFORE_MODEL);
        List<Hook> afterModelHooks = filterHooksByPosition(effectiveHooks, HookPosition.AFTER_MODEL);

        // 为 BEFORE_AGENT 类型的 Hook 创建图节点
        for (Hook hook : beforeAgentHooks) {
            if (hook instanceof AgentHook agentHook) {
                graph.addNode(Hook.getFullHookName(hook) + ".before", agentHook::beforeAgent);
            } else if (hook instanceof MessagesAgentHook messagesAgentHook) {
                graph.addNode(Hook.getFullHookName(hook) + ".before", MessagesAgentHook.beforeAgentAction(messagesAgentHook));
            }
        }

        // 为 AFTER_AGENT 类型的 Hook 创建图节点
        for (Hook hook : afterAgentHooks) {
            if (hook instanceof AgentHook agentHook) {
                graph.addNode(Hook.getFullHookName(hook) + ".after", agentHook::afterAgent);
            } else if (hook instanceof MessagesAgentHook messagesAgentHook) {
                graph.addNode(Hook.getFullHookName(hook) + ".after", MessagesAgentHook.afterAgentAction(messagesAgentHook));
            }
        }

        // 为 BEFORE_MODEL 类型的 Hook 创建图节点（在 LLM 调用前执行）
        for (Hook hook : beforeModelHooks) {
            if (hook instanceof ModelHook modelHook) {
                if (hook instanceof InterruptionHook interruptionHook) {
                    // InterruptionHook 特殊处理，用于人工介入场景
                    graph.addNode(Hook.getFullHookName(hook) + ".beforeModel", interruptionHook);
                } else {
                    graph.addNode(Hook.getFullHookName(hook) + ".beforeModel", modelHook::beforeModel);
                }
            } else if (hook instanceof MessagesModelHook messagesModelHook) {
                graph.addNode(Hook.getFullHookName(hook) + ".beforeModel", MessagesModelHook.beforeModelAction(messagesModelHook));
            }
        }

        // 为 AFTER_MODEL 类型的 Hook 创建图节点（在 LLM 调用后执行）
        for (Hook hook : afterModelHooks) {
            if (hook instanceof ModelHook modelHook) {
                if (hook instanceof HumanInTheLoopHook humanInTheLoopHook) {
                    // HumanInTheLoopHook 特殊处理，用于人工确认场景
                    graph.addNode(Hook.getFullHookName(hook) + ".afterModel", humanInTheLoopHook);
                } else {
                    graph.addNode(Hook.getFullHookName(hook) + ".afterModel", modelHook::afterModel);
                }
            } else if (hook instanceof MessagesModelHook messagesModelHook) {
                graph.addNode(Hook.getFullHookName(hook) + ".afterModel", MessagesModelHook.afterModelAction(messagesModelHook));
            }
        }

        // 确定节点执行流程的关键节点
        String entryNode = determineEntryNode(beforeAgentHooks, beforeModelHooks);      // 入口节点
        String loopEntryNode = determineLoopEntryNode(beforeModelHooks);                  // 循环入口节点（ReAct 循环开始）
        String loopExitNode = determineLoopExitNode(afterModelHooks);                     // 循环出口节点（ReAct 循环结束）
        String exitNode = determineExitNode(afterAgentHooks);                             // 退出节点

        // 建立节点之间的边连接关系
        graph.addEdge(START, entryNode);
        setupHookEdges(graph, beforeAgentHooks, afterAgentHooks, beforeModelHooks, afterModelHooks,
                entryNode, loopEntryNode, loopExitNode, exitNode, this);
        return graph;
    }

    /**
     * Setup and inject tools for hooks that implement ToolInjection interface.
     * Only the tool matching the hook's required tool name or type will be injected.
     *
     * @param hooks the list of hooks
     * @param toolNode the agent tool node containing available tools
     */
    private void setupToolsForHooks(List<? extends Hook> hooks, AgentToolNode toolNode) {
        if (hooks == null || hooks.isEmpty() || toolNode == null) {
            return;
        }

        List<ToolCallback> availableTools = toolNode.getToolCallbacks();
        if (availableTools == null || availableTools.isEmpty()) {
            return;
        }

        for (Hook hook : hooks) {
            if (hook instanceof ToolInjection toolInjection) {
                ToolCallback toolToInject = findToolForHook(toolInjection, availableTools);
                if (toolToInject != null) {
                    toolInjection.injectTool(toolToInject);
                }
            }
        }
    }

    /**
     * Find the matching tool based on hook's requirements.
     * Matching priority: 1) by name, 2) by type, 3) first available tool
     *
     * @param toolInjection the hook that needs a tool
     * @param availableTools all available tool callbacks
     * @return the matching tool, or null if no match found
     */
    private ToolCallback findToolForHook(ToolInjection toolInjection, List<ToolCallback> availableTools) {
        String requiredToolName = toolInjection.getRequiredToolName();
        Class<? extends ToolCallback> requiredToolType = toolInjection.getRequiredToolType();

        // Priority 1: Match by tool name
        if (requiredToolName != null) {
            for (ToolCallback tool : availableTools) {
                String toolName = tool.getToolDefinition().name();
                if (requiredToolName.equals(toolName)) {
                    return tool;
                }
            }
        }

        // Priority 2: Match by tool type
        if (requiredToolType != null) {
            for (ToolCallback tool : availableTools) {
                if (requiredToolType.isInstance(tool)) {
                    return tool;
                }
            }
        }

        // Priority 3: If no specific requirement, return the first available tool
        if (requiredToolName == null && requiredToolType == null && !availableTools.isEmpty()) {
            return availableTools.get(0);
        }

        return null;
    }

    /**
     * Filter hooks by their position based on @HookPositions annotation.
     * A hook will be included if its getHookPositions() contains the specified position.
     * If a hook implements Prioritized interface, it will be sorted by its order.
     * Hooks that don't implement Prioritized will maintain their original order.
     *
     * @param hooks the list of hooks to filter
     * @param position the position to filter by
     * @return list of hooks that should execute at the specified position
     */
    private static List<Hook> filterHooksByPosition(List<? extends Hook> hooks, HookPosition position) {
        List<Hook> filtered = hooks.stream()
                .filter(hook -> {
                    HookPosition[] positions = hook.getHookPositions();
                    return Arrays.asList(positions).contains(position);
                })
                .collect(Collectors.toList());

        // Separate hooks that implement Prioritized from those that don't
        List<Hook> prioritizedHooks = new ArrayList<>();
        List<Hook> nonPrioritizedHooks = new ArrayList<>();

        for (Hook hook : filtered) {
            if (hook instanceof Prioritized) {
                prioritizedHooks.add(hook);
            } else {
                nonPrioritizedHooks.add(hook);
            }
        }

        // Sort prioritized hooks by their order
        prioritizedHooks.sort(Comparator.comparingInt(h -> ((Prioritized) h).getOrder()));

        // Combine: prioritized hooks first (sorted), then non-prioritized hooks (original order)
        List<Hook> result = new ArrayList<>(prioritizedHooks);
        result.addAll(nonPrioritizedHooks);

        return result;
    }

    private static String determineEntryNode(
            List<Hook> agentHooks,
            List<Hook> modelHooks) {

        if (!agentHooks.isEmpty()) {
            return Hook.getFullHookName(agentHooks.get(0)) + ".before";
        } else if (!modelHooks.isEmpty()) {
            return Hook.getFullHookName(modelHooks.get(0)) + ".beforeModel";
        } else {
            return AGENT_MODEL_NAME;
        }
    }

    private static String determineLoopEntryNode(
            List<Hook> modelHooks) {

        if (!modelHooks.isEmpty()) {
            return Hook.getFullHookName(modelHooks.get(0)) + ".beforeModel";
        } else {
            return AGENT_MODEL_NAME;
        }
    }

    private static String determineLoopExitNode(
            List<Hook> modelHooks) {

        if (!modelHooks.isEmpty()) {
            return Hook.getFullHookName(modelHooks.get(0)) + ".afterModel";
        } else {
            return AGENT_MODEL_NAME;
        }
    }

    private static String determineExitNode(
            List<Hook> agentHooks) {

        if (!agentHooks.isEmpty()) {
            return Hook.getFullHookName(agentHooks.get(agentHooks.size() - 1)) + ".after";
        } else {
            return StateGraph.END;
        }
    }

    /**
     * 设置 Hook 之间的边连接关系
     * <p>
     * 该方法负责建立图中所有 Hook 节点之间的连接关系，包括：
     * 1. BEFORE_AGENT Hook 链：按顺序连接，最后一个连接到循环入口
     * 2. BEFORE_MODEL Hook 链：按顺序连接，最后一个连接到 Agent Model 节点
     * 3. AFTER_MODEL Hook 链：逆序连接（从后往前），第一个连接到退出节点
     * 4. AFTER_AGENT Hook 链：逆序连接（从后往前），第一个连接到退出节点
     * 5. 工具路由：如果有工具，设置工具调用和返回的路由逻辑
     * <p>
     * Hook 执行顺序：
     * <pre>
     * START → beforeAgentHooks → loopEntryNode → beforeModelHooks → AGENT_MODEL
     *        → afterModelHooks → (tool routing) → afterAgentHooks → END
     * </pre>
     *
     * @param graph 状态图实例
     * @param beforeAgentHooks BEFORE_AGENT 类型的 Hook 列表
     * @param afterAgentHooks AFTER_AGENT 类型的 Hook 列表
     * @param beforeModelHooks BEFORE_MODEL 类型的 Hook 列表
     * @param afterModelHooks AFTER_MODEL 类型的 Hook 列表
     * @param entryNode 入口节点名称
     * @param loopEntryNode 循环入口节点名称
     * @param loopExitNode 循环出口节点名称
     * @param exitNode 退出节点名称
     * @param agentInstance ReactAgent 实例，用于获取工具配置等信息
     * @throws GraphStateException 如果图状态异常
     */
    private static void setupHookEdges(
            StateGraph graph,
            List<Hook> beforeAgentHooks,
            List<Hook> afterAgentHooks,
            List<Hook> beforeModelHooks,
            List<Hook> afterModelHooks,
            String entryNode,
            String loopEntryNode,
            String loopExitNode,
            String exitNode,
            ReactAgent agentInstance) throws GraphStateException {

        // 连接 BEFORE_AGENT Hook 链：按顺序连接，最后一个连接到循环入口节点
        chainHook(graph, beforeAgentHooks, ".before", loopEntryNode, loopEntryNode, exitNode);

        // 连接 BEFORE_MODEL Hook 链：按顺序连接，最后一个连接到 Agent Model 节点
        chainHook(graph, beforeModelHooks, ".beforeModel", AGENT_MODEL_NAME, loopEntryNode, exitNode);

        // 连接 AFTER_MODEL Hook 链：逆序连接（从后往前），确保执行顺序正确
        if (!afterModelHooks.isEmpty()) {
            chainModelHookReverse(graph, afterModelHooks, ".afterModel", AGENT_MODEL_NAME, loopEntryNode, exitNode);
        }

        // 连接 AFTER_AGENT Hook 链：逆序连接（从后往前），确保执行顺序正确
        if (!afterAgentHooks.isEmpty()) {
            chainAgentHookReverse(graph, afterAgentHooks, ".after", exitNode, loopEntryNode, exitNode);
        }

        // 如果存在工具，设置工具路由逻辑（包括工具调用和返回的路由）
        if (agentInstance.hasTools) {
            setupToolRouting(graph, loopExitNode, loopEntryNode, exitNode, agentInstance);
        } else if (!loopExitNode.equals(AGENT_MODEL_NAME)) {
            // 没有工具但有 AFTER_MODEL Hook，连接到退出节点
            addHookEdge(graph, loopExitNode, exitNode, loopEntryNode, exitNode, afterModelHooks.get(afterModelHooks.size() - 1).canJumpTo());
        } else {
            // 没有工具也没有 AFTER_MODEL Hook，直接连接到退出节点
            graph.addEdge(loopExitNode, exitNode);
        }
    }

    /**
     * 逆序连接 Model Hook 链
     * <p>
     * 该方法用于连接 AFTER_MODEL 类型的 Hook，由于这些 Hook 需要在 LLM 调用后按添加顺序执行，
     * 但在图中需要从后往前连接，因此采用逆序连接策略。
     * <p>
     * 连接逻辑：
     * <pre>
     * 假设 hooks = [H1, H2, H3]，逆序连接后的图结构：
     *
     * defaultNext → H3.afterModel → H2.afterModel → H1.afterModel → modelDestination/endDestination
     *
     * 执行顺序（从前往后）：H1 → H2 → H3
     * 连接顺序（从后往前）：H3 → H2 → H1
     * </pre>
     * <p>
     * 每个 Hook 节点支持跳转到模型节点或结束节点（通过 canJumpTo 配置）。
     *
     * @param graph 状态图实例
     * @param hooks Model Hook 列表
     * @param nameSuffix 节点名称后缀（如 ".afterModel"）
     * @param defaultNext 默认下一个节点名称（通常是 loopExitNode）
     * @param modelDestination 模型节点目标名称
     * @param endDestination 结束节点目标名称
     * @throws GraphStateException 如果添加边时出现状态异常
     */
    private static void chainModelHookReverse(
            StateGraph graph,
            List<Hook> hooks,
            String nameSuffix,
            String defaultNext,
            String modelDestination,
            String endDestination) throws GraphStateException {

        // 将默认下一个节点连接到最后一个 Hook（逆序的第一个）
        graph.addEdge(defaultNext, Hook.getFullHookName(hooks.get(hooks.size() - 1)) + nameSuffix);

        // 逆序遍历 Hook 列表，将每个 Hook 连接到前一个 Hook
        for (int i = hooks.size() - 1; i > 0; i--) {
            Hook m1 = hooks.get(i);      // 当前 Hook（后面的）
            Hook m2 = hooks.get(i - 1);  // 前一个 Hook（前面的）
            addHookEdge(graph,
                    Hook.getFullHookName(m1) + nameSuffix,
                    Hook.getFullHookName(m2) + nameSuffix,
                    modelDestination, endDestination,
                    m1.canJumpTo());
        }
    }

    private static void chainAgentHookReverse(
            StateGraph graph,
            List<Hook> hooks,
            String nameSuffix,
            String defaultNext,
            String modelDestination,
            String endDestination) throws GraphStateException {
        if (!hooks.isEmpty()) {
            Hook first = hooks.get(0);
            addHookEdge(graph,
                    Hook.getFullHookName(first) + nameSuffix,
                    StateGraph.END,
                    modelDestination, endDestination,
                    first.canJumpTo());
        }

        for (int i = hooks.size() - 1; i > 0; i--) {
            Hook m1 = hooks.get(i);
            Hook m2 = hooks.get(i - 1);
            addHookEdge(graph,
                    Hook.getFullHookName(m1) + nameSuffix,
                    Hook.getFullHookName(m2) + nameSuffix,
                    modelDestination, endDestination,
                    m1.canJumpTo());
        }
    }

    /**
     * 按顺序连接 Hook 节点链
     * <p>
     * 该方法将 Hook 列表按顺序连接成一条链，每个 Hook 节点连接到下一个 Hook 节点，
     * 最后一个 Hook 节点连接到指定的默认下一个节点。
     * <p>
     * 连接逻辑：
     * <pre>
     * hook[0] → hook[1] → hook[2] → ... → hook[n-1] → defaultNext
     * </pre>
     * <p>
     * 每个 Hook 节点还支持跳转到模型节点或结束节点（通过 canJumpTo 配置）。
     *
     * @param graph 状态图实例
     * @param hooks Hook 列表，按执行顺序排列
     * @param nameSuffix Hook 节点名称后缀（如 ".before"、".beforeModel"）
     * @param defaultNext 默认下一个节点名称（最后一个 Hook 连接到的目标）
     * @param modelDestination 模型节点目标名称（用于跳转）
     * @param endDestination 结束节点目标名称（用于跳转）
     * @throws GraphStateException 如果图状态异常
     */
    private static void chainHook(
            StateGraph graph,
            List<Hook> hooks,
            String nameSuffix,
            String defaultNext,
            String modelDestination,
            String endDestination) throws GraphStateException {

        // 遍历 Hook 列表，将每个 Hook 连接到下一个 Hook
        for (int i = 0; i < hooks.size() - 1; i++) {
            Hook m1 = hooks.get(i);
            Hook m2 = hooks.get(i + 1);
            addHookEdge(graph,
                    Hook.getFullHookName(m1) + nameSuffix,
                    Hook.getFullHookName(m2) + nameSuffix,
                    modelDestination, endDestination,
                    m1.canJumpTo());
        }

        // 将最后一个 Hook 连接到默认下一个节点
        if (!hooks.isEmpty()) {
            Hook last = hooks.get(hooks.size() - 1);
            addHookEdge(graph,
                    Hook.getFullHookName(last) + nameSuffix,
                    defaultNext,
                    modelDestination, endDestination,
                    last.canJumpTo());
        }
    }

    /**
     * 为 Hook 节点添加边连接
     * <p>
     * 该方法负责为 Hook 节点添加边连接，支持两种连接方式：
     * 1. 条件边（Conditional Edges）- 当 Hook 支持跳转时，根据 jump_to 状态动态路由
     * 2. 普通边（Simple Edge）- 当 Hook 不支持跳转时，直接连接到默认目标
     * <p>
     * 条件边路由逻辑：
     * <pre>
     * 1. 检查状态中的 jump_to 值
     *    - 如果 jump_to 为 model → 跳转到模型节点
     *    - 如果 jump_to 为 end → 跳转到结束节点
     *    - 如果 jump_to 为 tool → 跳转到工具节点
     *    - 如果 jump_to 为空或无效 → 跳转到默认目标
     *
     * 2. 注册可用的目标节点
     *    - 默认目标始终可用
     *    - 根据 canJumpTo 配置注册其他目标（end、tool、model）
     * </pre>
     * <p>
     * 使用场景：
     * - InterruptionHook：支持跳转到 end（人工介入后终止）
     * - HumanInTheLoopHook：支持跳转到 model、end、tool（人工确认后继续或终止）
     * - 自定义 Hook：可根据业务需求配置跳转目标
     *
     * @param graph 状态图实例
     * @param name Hook 节点名称
     * @param defaultDestination 默认目标节点名称
     * @param modelDestination 模型节点目标名称
     * @param endDestination 结束节点目标名称
     * @param canJumpTo Hook 支持的跳转目标列表
     * @throws GraphStateException 如果添加边时出现状态异常
     */
    private static void addHookEdge(
            StateGraph graph,
            String name,
            String defaultDestination,
            String modelDestination,
            String endDestination,
            List<JumpTo> canJumpTo) throws GraphStateException {

        // 如果 Hook 支持跳转，添加条件边
        if (canJumpTo != null && !canJumpTo.isEmpty()) {
            // 创建路由函数，根据 jump_to 状态决定下一个节点
            EdgeAction router = state -> {
                // 从状态中获取 jump_to 值
                Object jumpToValue = state.value("jump_to").orElse(null);
                JumpTo jumpTo = null;
                if (jumpToValue != null) {
                    if (jumpToValue instanceof JumpTo) {
                        jumpTo = (JumpTo) jumpToValue;
                    } else if (jumpToValue instanceof String) {
                        jumpTo = JumpTo.fromStringOrNull((String) jumpToValue);
                    }
                }
                // 解析跳转目标
                return resolveJump(jumpTo, modelDestination, endDestination, defaultDestination);
            };

            // 注册可用的目标节点
            Map<String, String> destinations = new HashMap<>();
            destinations.put(defaultDestination, defaultDestination);  // 默认目标始终可用

            // 根据 canJumpTo 配置注册其他目标
            if (canJumpTo.contains(JumpTo.end)) {
                destinations.put(endDestination, endDestination);
            }
            if (canJumpTo.contains(JumpTo.tool)) {
                destinations.put(AGENT_TOOL_NAME, AGENT_TOOL_NAME);
            }
            if (canJumpTo.contains(JumpTo.model) && !name.equals(modelDestination)) {
                destinations.put(modelDestination, modelDestination);
            }

            // 添加条件边，使用 edge_async 包装为异步边
            graph.addConditionalEdges(name, edge_async(router), destinations);
        } else {
            // Hook 不支持跳转，添加普通边直接连接到默认目标
            graph.addEdge(name, defaultDestination);
        }
    }

    private static void setupToolRouting(
            StateGraph graph,
            String loopExitNode,
            String loopEntryNode,
            String exitNode,
            ReactAgent agentInstance) throws GraphStateException {

        // Model to tools routing
        graph.addConditionalEdges(loopExitNode, edge_async(agentInstance.makeModelToTools(loopEntryNode, exitNode)), Map.of(AGENT_TOOL_NAME, AGENT_TOOL_NAME, exitNode, exitNode, loopEntryNode, loopEntryNode));

        // Tools to model routing
        graph.addConditionalEdges(AGENT_TOOL_NAME, edge_async(agentInstance.makeToolsToModelEdge(loopEntryNode, exitNode)), Map.of(loopEntryNode, loopEntryNode, exitNode, exitNode));
    }

    private static String resolveJump(JumpTo jumpTo, String modelDestination, String endDestination, String defaultDestination) {
        if (jumpTo == null) {
            return defaultDestination;
        }

        return switch (jumpTo) {
            case model -> modelDestination;
            case end -> endDestination;
            case tool -> AGENT_TOOL_NAME;
        };
    }

    private KeyStrategyFactory buildMessagesKeyStrategyFactory(List<? extends Hook> hooks) {
        return () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            if (outputKey != null && !outputKey.isEmpty()) {
                keyStrategyHashMap.put(outputKey, outputKeyStrategy == null ? new ReplaceStrategy() : outputKeyStrategy);
            }
            keyStrategyHashMap.put("messages", new AppendStrategy());

            // Iterate through hooks and collect their key strategies
            if (hooks != null) {
                for (Hook hook : hooks) {
                    Map<String, KeyStrategy> hookStrategies = hook.getKeyStrategys();
                    if (hookStrategies != null && !hookStrategies.isEmpty()) {
                        keyStrategyHashMap.putAll(hookStrategies);
                    }
                }
            }

            return keyStrategyHashMap;
        };
    }

    /**
     * 创建模型输出到工具的路由决策器
     * <p>
     * 该方法返回一个 EdgeAction，用于在 ReAct 循环中决定下一步的执行方向。
     * 当 LLM 完成推理后，该方法根据消息内容判断是继续调用工具、回到模型继续推理，还是结束执行。
     * <p>
     * 路由决策优先级：
     * <pre>
     * 1. 检查 jump_to 指令（来自 afterModel Hook）
     *    - 允许 Hook 主动控制工作流走向
     *    - 支持跳转到：model（模型）、end（结束）、tool（工具）
     *
     * 2. 检查最后一条消息类型
     *    - AssistantMessage 且有工具调用 → 去工具节点执行
     *    - AssistantMessage 且无工具调用 → 结束执行
     *
     * 3. 检查工具响应完整性
     *    - 所有请求的工具都已执行 → 回到模型继续推理
     *    - 还有工具待执行 → 继续执行工具
     * </pre>
     * <p>
     * 典型执行流程：
     * <pre>
     * LLM 推理 → 返回工具调用 → makeModelToTools 判断 → 执行工具 → 工具返回
     *     → makeModelToTools 判断 → 所有工具完成 → 回到模型继续推理
     *     → LLM 无工具调用 → makeModelToTools 判断 → 结束
     * </pre>
     *
     * @param modelDestination 模型节点目标名称（继续推理时的跳转目标）
     * @param endDestination 结束节点目标名称（执行完成时的跳转目标）
     * @return EdgeAction 路由决策器，根据状态返回下一个节点名称
     */
    private EdgeAction makeModelToTools(String modelDestination, String endDestination) {
        return state -> {
            // 优先级 1：检查 jump_to 指令（来自 afterModel Hook）
            // 允许 Hook 主动控制工作流走向，实现人工介入、提前终止等场景
            Object jumpToValue = state.value("jump_to").orElse(null);
            if (jumpToValue != null) {
                JumpTo jumpTo = null;
                if (jumpToValue instanceof JumpTo) {
                    jumpTo = (JumpTo) jumpToValue;
                } else if (jumpToValue instanceof String) {
                    jumpTo = JumpTo.fromStringOrNull((String) jumpToValue);
                }

                // 如果存在有效的 jump_to 指令，立即执行跳转
                if (jumpTo != null) {
                    return switch (jumpTo) {
                        case model -> modelDestination;  // 跳转到模型节点，继续推理
                        case end -> endDestination;      // 跳转到结束节点，终止执行
                        case tool -> AGENT_TOOL_NAME;    // 跳转到工具节点，执行工具
                    };
                }
            }

            // 优先级 2：检查消息内容，根据 LLM 输出决定路由
            List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
            if (messages.isEmpty()) {
                logger.warn("No messages found in state when routing from model to tools");
                return endDestination;  // 无消息，默认结束
            }
            Message lastMessage = messages.get(messages.size() - 1);

            // 情况 1：最后一条消息是 AssistantMessage（LLM 输出）
            if (lastMessage instanceof AssistantMessage assistantMessage) {
                if (assistantMessage.hasToolCalls()) {
                    return AGENT_TOOL_NAME;  // LLM 请求调用工具 → 去工具节点
                } else {
                    return endDestination;   // LLM 无工具调用 → 结束执行
                }
            }
            // 情况 2：最后一条消息是 ToolResponseMessage（工具执行结果）
            else if (lastMessage instanceof ToolResponseMessage) {
                if (messages.size() < 2) {
                    // 正常情况下不应该出现，作为安全保护
                    throw new RuntimeException("Less than 2 messages in state when last message is ToolResponseMessage");
                }

                Message secondLastMessage = messages.get(messages.size() - 2);
                if (secondLastMessage instanceof AssistantMessage) {
                    AssistantMessage assistantMessage = (AssistantMessage) secondLastMessage;
                    ToolResponseMessage toolResponseMessage = (ToolResponseMessage) lastMessage;

                    if (assistantMessage.hasToolCalls()) {
                        // 获取 LLM 请求的所有工具调用 ID
                        Set<String> requestedToolIds = assistantMessage.getToolCalls().stream()
                                .map(AssistantMessage.ToolCall::id)
                                .collect(java.util.stream.Collectors.toSet());

                        // 获取已执行完成的工具响应 ID
                        Set<String> executedToolIds = toolResponseMessage.getResponses().stream()
                                .map(ToolResponseMessage.ToolResponse::id)
                                .collect(java.util.stream.Collectors.toSet());

                        // 判断所有请求的工具是否都已执行完成
                        if (executedToolIds.containsAll(requestedToolIds)) {
                            return modelDestination;  // 所有工具已完成 → 回到模型继续推理
                        } else {
                            return AGENT_TOOL_NAME;   // 还有工具待执行 → 继续执行工具
                        }
                    }
                }
            }

            // 默认情况：结束执行
            return endDestination;
        };
    }

    private EdgeAction makeToolsToModelEdge(String modelDestination, String endDestination) {
        return state -> {
            // 1. Extract last AI message and corresponding tool messages
            ToolResponseMessage toolResponseMessage = fetchLastToolResponseMessage(state);
            // 2. Exit condition: All executed tools have return_direct=True
            if (toolResponseMessage != null && !toolResponseMessage.getResponses().isEmpty()) {
                boolean allReturnDirect = toolResponseMessage.getResponses().stream().allMatch(toolResponse -> {
                    String toolName = toolResponse.name();
                    return false; // FIXME
                });
                if (allReturnDirect) {
                    return endDestination;
                }
            }

            // 3. Default: Continue the loop
            //    Tool execution completed successfully, route back to the model
            //    so it can process the tool results and decide the next action.
            return modelDestination;
        };
    }

    private ToolResponseMessage fetchLastToolResponseMessage(OverAllState state) {
        List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());

        ToolResponseMessage toolResponseMessage = null;

        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof ToolResponseMessage) {
                toolResponseMessage = (ToolResponseMessage) messages.get(i);
                break;
            }
        }

        return toolResponseMessage;
    }

    /**
     * Collects model interceptors from hooks (ModelHook and AgentHook) and merges them
     * with the current model interceptors.
     * <p>
     * If interceptors with the same name exist, the ones from ReactAgent configuration
     * take priority over those from hooks.
     *
     * @return merged list of model interceptors, or null if no interceptors exist
     */
    private List<ModelInterceptor> collectAndMergeModelInterceptors() {
        List<ModelInterceptor> result = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();

        // Add current model interceptors if they exist (higher priority)
        if (this.modelInterceptors != null && !this.modelInterceptors.isEmpty()) {
            for (ModelInterceptor interceptor : this.modelInterceptors) {
                result.add(interceptor);
                addedNames.add(interceptor.getName());
            }
        }

        // Collect interceptors from hooks (skip if name already exists)
        if (this.hooks != null && !this.hooks.isEmpty()) {
            for (Hook hook : this.hooks) {
                List<ModelInterceptor> hookInterceptors = hook.getModelInterceptors();
                if (hookInterceptors != null && !hookInterceptors.isEmpty()) {
                    for (ModelInterceptor interceptor : hookInterceptors) {
                        String name = interceptor.getName();
                        if (!addedNames.contains(name)) {
                            result.add(interceptor);
                            addedNames.add(name);
                        } else {
                            logger.info("Skipping model interceptor '{}' from hook '{}' because an interceptor with the same name already exists in ReactAgent configuration", name, hook.getName());
                        }
                    }
                }
            }
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * Collects tool interceptors from hooks (ModelHook and AgentHook) and merges them
     * with the current tool interceptors.
     * <p>
     * If interceptors with the same name exist, the ones from ReactAgent configuration
     * take priority over those from hooks.
     *
     * @return merged list of tool interceptors, or null if no interceptors exist
     */
    private List<ToolInterceptor> collectAndMergeToolInterceptors() {
        List<ToolInterceptor> result = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();

        // Add current tool interceptors if they exist (higher priority)
        if (this.toolInterceptors != null && !this.toolInterceptors.isEmpty()) {
            for (ToolInterceptor interceptor : this.toolInterceptors) {
                result.add(interceptor);
                addedNames.add(interceptor.getName());
            }
        }

        // Collect interceptors from hooks (skip if name already exists)
        if (this.hooks != null && !this.hooks.isEmpty()) {
            for (Hook hook : this.hooks) {
                List<ToolInterceptor> hookInterceptors = hook.getToolInterceptors();
                if (hookInterceptors != null && !hookInterceptors.isEmpty()) {
                    for (ToolInterceptor interceptor : hookInterceptors) {
                        String name = interceptor.getName();
                        if (!addedNames.contains(name)) {
                            result.add(interceptor);
                            addedNames.add(name);
                        } else {
                            logger.info("Skipping tool interceptor '{}' from hook '{}' because an interceptor with the same name already exists in ReactAgent configuration", name, hook.getName());
                        }
                    }
                }
            }
        }

        return result.isEmpty() ? null : result;
    }

    public String instruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
        llmNode.setInstruction(instruction);
    }

    public void setSystemPrompt(String systemPrompt) {
        llmNode.setSystemPrompt(systemPrompt);
    }

    public Map<String, Object> getThreadState(String threadId) {
        return threadIdStateMap.get(threadId);
    }

    public class AgentToSubCompiledGraphNodeAdapter implements NodeActionWithConfig, ResumableSubGraphAction {

        private String nodeId;

        private boolean includeContents;

        private boolean returnReasoningContents;

        private String instruction;

        private CompiledGraph childGraph;

        private CompileConfig parentCompileConfig;

        public AgentToSubCompiledGraphNodeAdapter(String nodeId, boolean includeContents, boolean returnReasoningContents,
                                                  CompiledGraph childGraph, String instruction, CompileConfig parentCompileConfig) {
            this.nodeId = nodeId;
            this.includeContents = includeContents;
            this.returnReasoningContents = returnReasoningContents;
            this.instruction = instruction;
            this.childGraph = childGraph;
            this.parentCompileConfig = parentCompileConfig;
        }

        @Override
        public String getResumeSubGraphId() {
            return resumeSubGraphId(nodeId);
        }

        @Override
        public Map<String, Object> apply(OverAllState parentState, RunnableConfig config) throws Exception {
            final boolean resumeSubgraph = config.metadata(resumeSubGraphId(nodeId), new TypeRef<Boolean>() {}).orElse(false);

            RunnableConfig subGraphRunnableConfig = getSubGraphRunnableConfig(config);
            Flux<GraphResponse<NodeOutput>> subGraphResult;
            Object parentMessages = null;

            // Instruction is always injected by InstructionAgentHook in beforeAgent; do not add here
            if (includeContents) {
                Map<String, Object> stateForChild = new HashMap<>(parentState.data());
                List<Object> newMessages;
                if (stateForChild.get("messages") != null) {
                    newMessages = new ArrayList<>((List<Object>)stateForChild.remove("messages"));
                } else {
                    newMessages = new ArrayList<>();
                }
                stateForChild.put("messages", newMessages);
                subGraphResult = childGraph.graphResponseStream(stateForChild, subGraphRunnableConfig);
            } else {
                Map<String, Object> stateForChild = new HashMap<>(parentState.data());
                parentMessages = stateForChild.remove("messages");
                subGraphResult = childGraph.graphResponseStream(stateForChild, subGraphRunnableConfig);
            }

            Map<String, Object> result = new HashMap<>();

            String outputKeyToParent = StringUtils.hasLength(ReactAgent.this.outputKey) ? ReactAgent.this.outputKey : "messages";
            result.put(outputKeyToParent, getGraphResponseFlux(parentState, subGraphResult, null));
            return result;
        }

        private @NotNull Flux<GraphResponse<NodeOutput>> getGraphResponseFlux(OverAllState parentState, Flux<GraphResponse<NodeOutput>> subGraphResult, AgentInstructionMessage instructionMessage) {
            // Use buffer(2, 1) to create sliding windows: [elem0, elem1], [elem1, elem2], ..., [elemN-1, elemN], [elemN]
            // For windows with 2 elements, emit the first (previous element)
            // For the last window with 1 element, process it specially
            return subGraphResult
                    .buffer(2, 1)
                    .flatMap(window -> {
                        if (window.size() == 1) {
                            // Last window: process the last element with message filtering
                            return Flux.just(processLastResponse(window.get(0), parentState, instructionMessage));
                        } else {
                            // Regular window: emit the first element (previous, delayed by one)
                            return Flux.just(window.get(0));
                        }
                    }, 1); // Concurrency of 1 to maintain order
        }

        /**
         * Process the last response by filtering messages based on parent state and returnReasoningContents flag.
         *
         * @param lastResponse the last response from sub-graph
         * @param parentState the parent state containing messages to filter out
         * @return processed GraphResponse with filtered messages
         */
        private GraphResponse<NodeOutput> processLastResponse(GraphResponse<NodeOutput> lastResponse, OverAllState parentState, AgentInstructionMessage instructionMessage) {
            if (lastResponse == null) {
                return lastResponse;
            }

            if (lastResponse.resultValue().isPresent()) {
                Object resultValue = lastResponse.resultValue().get();
                if (resultValue instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (Map<String, Object>) resultValue;
                    if (resultMap.get("messages") instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> messages = new ArrayList<>((List<Object>) resultMap.get("messages"));
                        if (!messages.isEmpty()) {
                            parentState.value("messages").ifPresent(parentMsgs -> {
                                if (parentMsgs instanceof List) {
                                    messages.removeAll((List<?>) parentMsgs);
                                }
                            });

                            List<Object> finalMessages;
                            if (returnReasoningContents) {
                                finalMessages = messages;
                            } else {
                                if (!messages.isEmpty()) {
                                    if (instructionMessage != null) {
                                        finalMessages = new ArrayList<>();
                                        finalMessages.add(instructionMessage);
                                        finalMessages.add(messages.get(messages.size() - 1));
                                    } else {
                                        finalMessages = List.of(messages.get(messages.size() - 1));
                                    }
                                } else {
                                    finalMessages = List.of();
                                }
                            }

                            Map<String, Object> newResultMap = new HashMap<>(resultMap);
                            newResultMap.put("messages", finalMessages);
                            return GraphResponse.done(newResultMap);
                        }
                    }
                }
            }
            return lastResponse;
        }

        private RunnableConfig getSubGraphRunnableConfig(RunnableConfig config) {
            RunnableConfig subGraphRunnableConfig = RunnableConfig.builder(config)
                    .checkPointId(null)
                    .nextNode(null)
                    .addMetadata("_AGENT_", subGraphId(nodeId)) // subGraphId is the same as the name of the agent that created it
                    .build();
            subGraphRunnableConfig.clearContext();
            var parentSaver = parentCompileConfig.checkpointSaver();
            var subGraphSaver = childGraph.compileConfig.checkpointSaver();

            if (subGraphSaver.isPresent()) {
                if (parentSaver.isEmpty()) {
                    throw new IllegalStateException("Missing CheckpointSaver in parent graph!");
                }

                // Check saver are the same instance
                if (parentSaver.get() == subGraphSaver.get()) {
                    subGraphRunnableConfig = RunnableConfig.builder(config)
                            .threadId(config.threadId()
                                    .map(threadId -> format("%s_%s", threadId, subGraphId(nodeId)))
                                    .orElseGet(() -> subGraphId(nodeId)))
                            .nextNode(null)
                            .checkPointId(null)
                            .addMetadata("_AGENT_", subGraphId(nodeId)) // subGraphId is the same as the name of the agent that created it
                            .build();
                    subGraphRunnableConfig.clearContext();
                }
            }
            return subGraphRunnableConfig;
        }
    }

    /**
     * Internal class that adapts a ReactAgent to be used as a SubGraph Node.
     */
    private class AgentSubGraphNode extends Node implements SubGraphNode {

        private final CompiledGraph subGraph;

        public AgentSubGraphNode(String id, boolean includeContents, boolean returnReasoningContents, CompiledGraph subGraph, String instruction) {
            super(Objects.requireNonNull(id, "id cannot be null"),
                    (config) -> node_async(new AgentToSubCompiledGraphNodeAdapter(id, includeContents, returnReasoningContents, subGraph, instruction, config)));
            this.subGraph = subGraph;
        }

        @Override
        public StateGraph subGraph() {
            return subGraph.stateGraph;
        }

        @Override
        public Map<String, KeyStrategy> keyStrategies() {
            return subGraph.getKeyStrategyMap();
        }
    }
}