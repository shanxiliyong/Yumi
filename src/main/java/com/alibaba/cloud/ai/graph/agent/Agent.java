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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.scheduling.ScheduleConfig;
import com.alibaba.cloud.ai.graph.scheduling.ScheduledAgentTask;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;

import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import reactor.core.publisher.Flux;

import org.springframework.scheduling.Trigger;

import static com.alibaba.cloud.ai.graph.utils.Messageutils.convertToMessages;

/**
 * LangGraph Agent 抽象基类
 * <p>
 * 所有 Agent 实现的父类，包含 Agent 的通用属性和方法。
 * 提供图的初始化、编译、执行等核心功能。
 */
public abstract class Agent {

    /** Agent 名称，在图中必须是唯一标识符 */
    protected String name;

    /**
     * Agent 能力描述，用于系统在委托控制给不同 Agent 时进行决策
     */
    protected String description;

    /** 图编译配置，用于自定义编译行为 */
    protected CompileConfig compileConfig;

    /** 编译后的图实例，使用 volatile 保证多线程可见性 */
    protected volatile CompiledGraph compiledGraph;

    /** 状态图实例，使用 volatile 保证多线程可见性 */
    protected volatile StateGraph graph;

    /** 并行节点执行器，用于控制并行任务的执行 */
    protected Executor executor;

    /**
     * Protected constructor for initializing all base agent properties.
     * @param name the unique name of the agent
     * @param description the description of the agent's capability
     */
    protected Agent(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Default protected constructor for subclasses that need to initialize properties
     * differently.
     */
    protected Agent() {
        // Allow subclasses to initialize properties through other means
    }

    /**
     * Gets the agent's unique name.
     * @return the unique name of the agent.
     */
    public String name() {
        return name;
    }

    /**
     * Gets the one-line description of the agent's capability.
     * @return the description of the agent.
     */
    public String description() {
        return description;
    }

    public StateGraph getGraph() {
        if (this.graph == null) {
            try {
                this.graph = initGraph();
            }
            catch (GraphStateException e) {
                throw new RuntimeException(e);
            }
        }
        return this.graph;
    }

    /**
     * 获取并编译图，返回编译后的 CompiledGraph 实例
     * <p>
     * 该方法采用懒加载 + 单例模式，确保图只被编译一次：
     * 1. 如果 compiledGraph 已存在，直接返回（避免重复编译）
     * 2. 否则获取 StateGraph 并进行编译
     * 3. 根据是否有 compileConfig 选择不同的编译方式
     *
     * @return 编译后的 CompiledGraph 实例，可用于执行图任务
     * @throws RuntimeException 如果图编译过程中发生 GraphStateException
     */
    public synchronized CompiledGraph getAndCompileGraph() {
        // 如果已编译，直接返回缓存的实例（单例模式）
        if (compiledGraph != null) {
            return compiledGraph;
        }

        // 获取 StateGraph 实例（可能触发 initGraph 抽象方法）
        StateGraph graph = getGraph();
        try {
            // 根据是否有编译配置，选择不同的编译方式
            if (this.compileConfig == null) {
                // 使用默认配置编译图
                this.compiledGraph = graph.compile();
            }
            else {
                // 使用自定义编译配置编译图
                this.compiledGraph = graph.compile(this.compileConfig);
            }
        } catch (GraphStateException e) {
            // 图状态异常，包装为运行时异常抛出
            throw new RuntimeException(e);
        }
        return this.compiledGraph;
    }

    /**
     * Schedule the agent task with trigger.
     * @param trigger the schedule configuration
     * @param input the agent input
     * @return a ScheduledAgentTask instance for managing the scheduled task
     */
    public ScheduledAgentTask schedule(Trigger trigger, Map<String, Object> input)
            throws GraphStateException, GraphRunnerException {
        ScheduleConfig scheduleConfig = ScheduleConfig.builder().trigger(trigger).inputs(input).build();
        return schedule(scheduleConfig);
    }

    /**
     * Schedule the agent task with trigger.
     * @param scheduleConfig the schedule configuration
     * @return a ScheduledAgentTask instance for managing the scheduled task
     */
    public ScheduledAgentTask schedule(ScheduleConfig scheduleConfig) throws GraphStateException {
        CompiledGraph compiledGraph = getAndCompileGraph();
        return compiledGraph.schedule(scheduleConfig);
    }

    public StateSnapshot getCurrentState(RunnableConfig config) throws GraphRunnerException {
        return compiledGraph.getState(config);
    }

    // ------------------- Invoke with OverAllState as return value -------------------

    public Optional<OverAllState> invoke(String message) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doInvoke(inputs, null);
    }

    public Optional<OverAllState> invoke(String message, RunnableConfig config) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doInvoke(inputs, config);
    }

    public Optional<OverAllState> invoke(UserMessage message) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doInvoke(inputs, null);
    }

    public Optional<OverAllState> invoke(UserMessage message, RunnableConfig config) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doInvoke(inputs, config);
    }

    public Optional<OverAllState> invoke(List<Message> messages) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(messages);
        return doInvoke(inputs, null);
    }

    public Optional<OverAllState> invoke(List<Message> messages, RunnableConfig config) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(messages);
        return doInvoke(inputs, config);
    }

    /**
     * Invokes the agent with the given inputs map.
     * <p>
     * When you need to pass additional parameters beyond {@code messages} and {@code input},
     * use this overload.
     * <p>
     * Reserved keys: {@code messages} and {@code input} are used as question/input for the
     * agent. Other keys can be arbitrary and are passed as graph state, e.g. for prompt
     * placeholders or any other state values.
     *
     * @param inputs the input map (reserved: messages, input; other keys as state)
     * @return the resulting overall state, or empty if none
     * @throws GraphRunnerException if the graph execution fails
     */
    public Optional<OverAllState> invoke(Map<String, Object> inputs) throws GraphRunnerException {
        return doInvoke(inputs, null);
    }

    /**
     * Invokes the agent with the given inputs map and runtime config.
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
     * @return the resulting overall state, or empty if none
     * @throws GraphRunnerException if the graph execution fails
     */
    public Optional<OverAllState> invoke(Map<String, Object> inputs, RunnableConfig config) throws GraphRunnerException {
        return doInvoke(inputs, config);
    }

    // ------------------- Invoke  methods with Output as return value -------------------

    public Optional<NodeOutput> invokeAndGetOutput(String message) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doInvokeAndGetOutput(inputs, null);
    }

    public Optional<NodeOutput> invokeAndGetOutput(String message, RunnableConfig config) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doInvokeAndGetOutput(inputs, config);
    }

    public Optional<NodeOutput> invokeAndGetOutput(UserMessage message) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doInvokeAndGetOutput(inputs, null);
    }

    public Optional<NodeOutput> invokeAndGetOutput(UserMessage message, RunnableConfig config) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doInvokeAndGetOutput(inputs, config);
    }

    public Optional<NodeOutput> invokeAndGetOutput(List<Message> messages) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(messages);
        return doInvokeAndGetOutput(inputs, null);
    }

    public Optional<NodeOutput> invokeAndGetOutput(List<Message> messages, RunnableConfig config) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(messages);
        return doInvokeAndGetOutput(inputs, config);
    }

    /**
     * Invokes the agent with the given inputs map and returns the node output.
     * <p>
     * When you need to pass additional parameters beyond {@code messages} and {@code input},
     * use this overload.
     * <p>
     * Reserved keys: {@code messages} and {@code input} are used as question/input for the
     * agent. Other keys can be arbitrary and are passed as graph state, e.g. for prompt
     * placeholders or any other state values.
     *
     * @param inputs the input map (reserved: messages, input; other keys as state)
     * @return the node output, or empty if none
     * @throws GraphRunnerException if the graph execution fails
     */
    public Optional<NodeOutput> invokeAndGetOutput(Map<String, Object> inputs) throws GraphRunnerException {
        return doInvokeAndGetOutput(inputs, null);
    }

    /**
     * Invokes the agent with the given inputs map and runtime config, returns the node output.
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
     * @return the node output, or empty if none
     * @throws GraphRunnerException if the graph execution fails
     */
    public Optional<NodeOutput> invokeAndGetOutput(Map<String, Object> inputs, RunnableConfig config) throws GraphRunnerException {
        return doInvokeAndGetOutput(inputs, config);
    }

    // ------------------- Message Stream methods -------------------

    /**
     * Streams the execution result as a {@link Flux} of {@link Message} objects
     * using a plain text input.
     * <p>
     * This is a convenience API built on top of {@code stream(...)} that extracts
     * and emits {@link Message} instances directly instead of low-level
     * {@code NodeOutput} objects. It is intended for use cases that only care
     * about the generated messages and do not require access to graph
     * orchestration or node execution details.
     *
     * @param message the input message as plain text
     * @return a {@link Flux} emitting {@link Message} objects as they are produced
     * @throws GraphRunnerException if the graph execution fails
     */
    public Flux<Message> streamMessages(String message) throws GraphRunnerException {
        return stream(message)
                .transform(this::extractMessages);
    }

    /**
     * Streams the execution result as a {@link Flux} of {@link Message} objects
     * using a plain text input and a custom {@link RunnableConfig}.
     *
     * @param message the input message as plain text
     * @param config runtime configuration controlling execution behavior
     * @return a {@link Flux} emitting {@link Message} objects as they are produced
     * @throws GraphRunnerException if the graph execution fails
     */
    public Flux<Message> streamMessages(String message, RunnableConfig config) throws GraphRunnerException {
        return stream(message, config)
                .transform(this::extractMessages);
    }

    /**
     * Streams the execution result as a {@link Flux} of {@link Message} objects
     * using a {@link UserMessage} as input.
     *
     * @param message the user message input
     * @return a {@link Flux} emitting {@link Message} objects as they are produced
     * @throws GraphRunnerException if the graph execution fails
     */
    public Flux<Message> streamMessages(UserMessage message) throws GraphRunnerException {
        return stream(message)
                .transform(this::extractMessages);
    }

    /**
     * Streams the execution result as a {@link Flux} of {@link Message} objects
     * using a {@link UserMessage} as input and a custom {@link RunnableConfig}.
     *
     * @param message the user message input
     * @param config runtime configuration controlling execution behavior
     * @return a {@link Flux} emitting {@link Message} objects as they are produced
     * @throws GraphRunnerException if the graph execution fails
     */
    public Flux<Message> streamMessages(UserMessage message, RunnableConfig config) throws GraphRunnerException {
        return stream(message, config)
                .transform(this::extractMessages);
    }

    /**
     * Streams the execution result as a {@link Flux} of {@link Message} objects
     * using a list of input {@link Message} instances.
     *
     * @param messages the input messages
     * @return a {@link Flux} emitting {@link Message} objects as they are produced
     * @throws GraphRunnerException if the graph execution fails
     */
    public Flux<Message> streamMessages(List<Message> messages) throws GraphRunnerException {
        return stream(messages)
                .transform(this::extractMessages);
    }

    /**
     * Streams the execution result as a {@link Flux} of {@link Message} objects
     * using a list of input {@link Message} instances and a custom
     * {@link RunnableConfig}.
     *
     * @param messages the input messages
     * @param config runtime configuration controlling execution behavior
     * @return a {@link Flux} emitting {@link Message} objects as they are produced
     * @throws GraphRunnerException if the graph execution fails
     */
    public Flux<Message> streamMessages(List<Message> messages, RunnableConfig config) throws GraphRunnerException {
        return stream(messages, config)
                .transform(this::extractMessages);
    }

    /**
     * Streams the execution result as a {@link Flux} of {@link Message} objects using an
     * inputs map.
     * <p>
     * When you need to pass additional parameters beyond {@code messages} and {@code input},
     * use this overload.
     * <p>
     * Reserved keys: {@code messages} and {@code input} are used as question/input for the
     * agent. Other keys can be arbitrary and are passed as graph state, e.g. for prompt
     * placeholders or any other state values.
     *
     * @param inputs the input map (reserved: messages, input; other keys as state)
     * @return a {@link Flux} emitting {@link Message} objects as they are produced
     * @throws GraphRunnerException if the graph execution fails
     */
    public Flux<Message> streamMessages(Map<String, Object> inputs) throws GraphRunnerException {
        return stream(inputs).transform(this::extractMessages);
    }

    /**
     * Streams the execution result as a {@link Flux} of {@link Message} objects using an
     * inputs map and a custom {@link RunnableConfig}.
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
     * @return a {@link Flux} emitting {@link Message} objects as they are produced
     * @throws GraphRunnerException if the graph execution fails
     */
    public Flux<Message> streamMessages(Map<String, Object> inputs, RunnableConfig config) throws GraphRunnerException {
        return stream(inputs, config).transform(this::extractMessages);
    }

    // ------------------- Stream methods -------------------

    public Flux<NodeOutput> stream(String message) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doStream(inputs, buildStreamConfig(null));
    }

    public Flux<NodeOutput> stream(String message, RunnableConfig config) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doStream(inputs, config);
    }

    public Flux<NodeOutput> stream(UserMessage message) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doStream(inputs, buildStreamConfig(null));
    }

    public Flux<NodeOutput> stream(UserMessage message, RunnableConfig config) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(message);
        return doStream(inputs, config);
    }

    public Flux<NodeOutput> stream(List<Message> messages) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(messages);
        return doStream(inputs, buildStreamConfig(null));
    }

    public Flux<NodeOutput> stream(List<Message> messages, RunnableConfig config) throws GraphRunnerException {
        Map<String, Object> inputs = buildMessageInput(messages);
        return doStream(inputs, config);
    }

    /**
     * Streams the graph execution with the given inputs map.
     * <p>
     * When you need to pass additional parameters beyond {@code messages} and {@code input},
     * use this overload.
     * <p>
     * Reserved keys: {@code messages} and {@code input} are used as question/input for the
     * agent. Other keys can be arbitrary and are passed as graph state, e.g. for prompt
     * placeholders or any other state values.
     *
     * @param inputs the input map (reserved: messages, input; other keys as state)
     * @return a {@link Flux} emitting {@link NodeOutput} as they are produced
     * @throws GraphRunnerException if the graph execution fails
     */
    public Flux<NodeOutput> stream(Map<String, Object> inputs) throws GraphRunnerException {
        return doStream(inputs, buildStreamConfig(null));
    }

    /**
     * Streams the graph execution with the given inputs map and runtime config.
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
     * @return a {@link Flux} emitting {@link NodeOutput} as they are produced
     * @throws GraphRunnerException if the graph execution fails
     */
    public Flux<NodeOutput> stream(Map<String, Object> inputs, RunnableConfig config) throws GraphRunnerException {
        return doStream(inputs, config);
    }

    protected Optional<OverAllState> doInvoke(Map<String, Object> input, RunnableConfig runnableConfig) {
        CompiledGraph compiledGraph = getAndCompileGraph();
        return compiledGraph.invoke(input, buildNonStreamConfig(runnableConfig));
    }

    protected Optional<NodeOutput> doInvokeAndGetOutput(Map<String, Object> input, RunnableConfig runnableConfig) {
        CompiledGraph compiledGraph = getAndCompileGraph();
        return compiledGraph.invokeAndGetOutput(input, buildNonStreamConfig(runnableConfig));
    }

    protected Flux<NodeOutput> doStream(Map<String, Object> input, RunnableConfig runnableConfig) {
        CompiledGraph compiledGraph = getAndCompileGraph();
        return compiledGraph.stream(input, buildStreamConfig(runnableConfig));
    }

    protected RunnableConfig buildNonStreamConfig(RunnableConfig config) {
        RunnableConfig.Builder builder = config == null
                ? RunnableConfig.builder()
                : RunnableConfig.builder(config);

        builder.addMetadata("_stream_", false).addMetadata("_AGENT_", name);
        applyExecutorConfig(builder);

        return builder.build();
    }

    protected RunnableConfig buildStreamConfig(RunnableConfig config) {
        RunnableConfig.Builder builder = config == null
                ? RunnableConfig.builder()
                : RunnableConfig.builder(config);

        builder.addMetadata("_AGENT_", name);
        applyExecutorConfig(builder);

        return builder.build();
    }

    /**
     * Applies executor configuration to the RunnableConfig builder.
     * This method sets the default executor for parallel nodes from the agent's configuration.
     * @param builder the RunnableConfig builder to apply executor configuration to
     */
    protected void applyExecutorConfig(RunnableConfig.Builder builder) {
        if (executor != null) {
            builder.defaultParallelExecutor(executor);
        }
    }

    /**
     * 构建消息输入参数，用于 LangGraph 节点执行
     * <p>
     * 该方法将用户输入的消息转换为 LangGraph 所需的输入格式：
     * 1. 将消息转换为 List&lt;Message&gt; 格式
     * 2. 提取最后一条用户消息作为 "input" 参数
     * 3. 将所有消息作为 "messages" 参数
     *
     * @param message 用户输入的消息，可以是 String、Message 或 List&lt;Message&gt;
     * @return 包含 messages 和 input 的输入参数 Map
     */
    protected Map<String, Object> buildMessageInput(Object message) {
        // 将输入消息转换为标准消息列表
        List<Message> messages;
        if (message instanceof List) {
            // 如果已经是消息列表，直接使用
            messages = (List<Message>) message;
        } else {
            // 否则调用转换方法，支持 String、Message 等多种输入类型
            messages = convertToMessages(message);
        }

        // 构建输入参数 Map
        Map<String, Object> inputs = new HashMap<>();
        // 将所有消息放入 inputs，供 LangGraph 的 MessagesState 使用
        inputs.put("messages", messages);

        // 从消息列表中查找最后一条用户消息，作为当前轮次的输入
        UserMessage lastUserMessage = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage) {
                lastUserMessage = (UserMessage) msg;
                break;
            }
        }
        // 如果找到用户消息，将其文本内容作为 "input" 参数
        // 这样 LangGraph 节点可以通过 state.get("input") 获取用户输入
        if (lastUserMessage != null) {
            inputs.put("input", lastUserMessage.getText());
        }
        return inputs;
    }

    protected abstract StateGraph initGraph() throws GraphStateException;

    /**
     * Extracts {@link Message} objects from a stream of {@link NodeOutput}.
     * <p>
     * This helper method filters the incoming {@link NodeOutput} stream to retain only
     * {@link StreamingOutput} instances whose {@link OutputType} is intended to expose
     * messages at the Agent API level ({@code AGENT_MODEL_STREAMING} or
     * {@code AGENT_TOOL_FINISHED}), and whose embedded {@link Message} is non-null.
     * <p>
     * All other {@link NodeOutput} types (such as tool or hook intermediate outputs)
     * are intentionally filtered out to avoid leaking graph-level implementation
     * details to Agent API consumers.
     *
     * @param stream the stream of {@link NodeOutput} produced during graph execution
     * @return a {@link Flux} emitting only user-facing {@link Message} instances
     */
    private Flux<Message> extractMessages(Flux<NodeOutput> stream) {
        return stream.filter(o -> o instanceof StreamingOutput<?> so
                        && isMessageOutputType(so.getOutputType())
                        && so.message() != null)
                .map(o -> ((StreamingOutput<?>) o).message());
    }

    /**
     * Checks whether the given {@link OutputType} indicates a message-type output.
     * <p>
     * include {@link OutputType#AGENT_MODEL_STREAMING} and {@link OutputType#AGENT_TOOL_FINISHED}.
     *
     * @param type the {@link OutputType} to check
     * @return true if the output type is a message-type output, false otherwise
     */
    private boolean isMessageOutputType(OutputType type) {
        return type == OutputType.AGENT_MODEL_STREAMING
                || type == OutputType.AGENT_TOOL_FINISHED;
    }

}