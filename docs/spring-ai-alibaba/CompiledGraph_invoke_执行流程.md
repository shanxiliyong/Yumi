# CompiledGraph.invoke 方法执行流程图

## 方法签名

```java
public Optional<OverAllState> invoke(Map<String, Object> inputs, RunnableConfig config)
```

## 方法说明

同步执行图并返回最终状态。该方法封装了流式执行，通过阻塞等待获取最终结果。

## 执行流程

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                    invoke(inputs, config)                                │
│                                                                                           │
│  输入:                                                                                    │
│    - inputs: Map<String, Object>  初始输入参数                                            │
│    - config: RunnableConfig       运行配置（线程ID、检查点等）                             │
│                                                                                           │
│  返回:                                                                                    │
│    - Optional<OverAllState>       包含最终状态的 Optional                                  │
─────────────────────────────────────────────────────────────────────────────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  步骤 1: stream(inputs, config)                                                          │
│                                                                                           │
│  启动流式执行，返回 Flux<NodeOutput>                                                      │
│                                                                                           │
│  Flux<NodeOutput> 是一个响应式流，包含图执行过程中每个节点的输出                           │
│                                                                                           │
│  NodeOutput 包含:                                                                         │
│    - node: String           当前执行的节点名称                                            │
│    - state: OverAllState    当前状态                                                      │
│    - ...                                                                                │
─────────────────────────────────────────────────────────────────────────────────────────┘
                                              │
                                              ▼
─────────────────────────────────────────────────────────────────────────────────────────┐
│  步骤 2: .last()                                                                         │
│                                                                                           │
│  从 Flux 流中取最后一个元素（最终状态）                                                    │
│                                                                                           │
│  返回: Mono<NodeOutput>                                                                   │
│                                                                                           │
│  注意:                                                                                    │
│    - 如果流为空，返回空的 Mono                                                             │
│    - 如果流有多个元素，只取最后一个                                                        │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  步骤 3: .map(NodeOutput::state)                                                         │
│                                                                                           │
│  从 NodeOutput 中提取 OverAllState                                                        │
│                                                                                           │
│  返回: Mono<OverAllState>                                                                 │
│                                                                                           │
│  OverAllState 包含:                                                                       │
│    - values: Map<String, Object>  状态中所有键值对                                         │
│    - ...                                                                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  步骤 4: .block()                                                                        │
│                                                                                           │
│  阻塞等待 Mono 完成，获取实际值                                                            │
│                                                                                           │
│  返回: OverAllState 或 null                                                               │
│                                                                                           │
│  注意:                                                                                    │
│    - 这是一个阻塞调用，会等待整个图执行完成                                                 │
│    - 如果 Mono 为空，返回 null                                                             │
│    - 如果执行过程中发生异常，会抛出异常                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  步骤 5: Optional.ofNullable()                                                           │
│                                                                                           │
│  将可能为 null 的结果包装为 Optional                                                      │
│                                                                                           │
│  返回: Optional<OverAllState>                                                             │
│                                                                                           │
│  - 如果 block() 返回非 null → Optional 包含该值                                           │
│  - 如果 block() 返回 null    → Optional.empty()                                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## 完整调用链

```
invoke(inputs, config)
    │
    ├── stream(inputs, config)
    │       │
    │       ├── 初始化状态: OverAllState.stateFactory(keyStrategyMap).apply(inputs)
    │       │
    │       ├── 创建执行器: SingleThreadExecutor
    │       │
    │       ├── 获取入口节点: getEntryPointNode()
    │       │
    │       ├── 循环执行节点 (最多 maxIterations 次)
    │       │       │
    │       │       ├── 执行当前节点: executeNode(nodeName, state, config)
    │       │       │       │
    │       │       │       ├── 获取节点工厂: nodeFactories.get(nodeName)
    │       │       │       │
    │       │       │       ├── 创建节点实例: factory.create()
    │       │       │       │
    │       │       │       ├── 执行节点: nodeAction.apply(state, config)
    │       │       │       │       │
    │       │       │       │       └── 返回: Map<String, Object> (状态更新)
    │       │       │       │
    │       │       │       └── 更新状态: state.update(updateMap)
    │       │       │
    │       │       ├── 发射节点输出: Flux.just(NodeOutput.of(nodeName, state))
    │       │       │
    │       │       ├── 获取下一个节点: getNextNode(nodeName, state)
    │       │       │       │
    │       │       │       ├── 检查条件边: edges.get(nodeName)
    │       │       │       │
    │       │       │       ├── 如果是条件边: EdgeValue.conditional
    │       │       │       │       │
    │       │       │       │       └── 执行路由函数: edgeAction.apply(state)
    │       │       │       │               │
    │       │       │       │               └── 返回下一个节点名称
    │       │       │       │
    │       │       │       └── 如果是普通边: EdgeValue.target
    │       │       │               │
    │       │       │               └── 返回目标节点名称
    │       │       │
    │       │       └── 判断是否结束
    │       │               │
    │       │               ├── 下一个节点是 END → 结束循环
    │       │               │
    │       │               ├── 达到最大迭代次数 → 抛出异常
    │       │               │
    │       │               └── 否则 → 继续下一轮循环
    │       │
    │       └── 返回: Flux<NodeOutput>
    │
    ├── .last() → Mono<NodeOutput>
    │
    ├── .map(NodeOutput::state) → Mono<OverAllState>
    │
    ├── .block() → OverAllState 或 null
    │
    └── Optional.ofNullable() → Optional<OverAllState>
```

## 状态更新机制

```
初始状态
    │
    ├── inputs: {"input": "用户问题"}
    │
    ▼
─────────────────────────────────────────────────────────────────────────────────────────┐
│  第一轮循环                                                                                │
│                                                                                           │
│  执行节点: AGENT_MODEL                                                                    │
│    │                                                                                      │
│    ├── 调用 LLM: chatModel.call(messages)                                                 │
│    │                                                                                      │
│    ├── 返回: {"messages": [AssistantMessage]}                                             │
│    │                                                                                      │
│    └── 状态更新: state.update({"messages": [...]})                                        │
│            │                                                                              │
│            ├── messages 键使用 AppendStrategy（追加策略）                                  │
│            │                                                                              │
│            └── 新消息追加到现有消息列表                                                    │
│                                                                                           │
│  路由判断: makeModelToTools(state)                                                        │
│    │                                                                                      │
│    ├── 检查最后一条消息是否有工具调用                                                       │
│    │                                                                                      │
│    └── 有工具调用 → 下一个节点: AGENT_TOOL                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  第二轮循环                                                                                │
│                                                                                           │
│  执行节点: AGENT_TOOL                                                                     │
│    │                                                                                      │
│    ├── 获取工具调用: assistantMessage.getToolCalls()                                      │
│    │                                                                                      │
│    ├── 执行工具: tool.execute(toolCall)                                                   │
│    │                                                                                      │
│    ├── 返回: {"messages": [ToolResponseMessage]}                                          │
│    │                                                                                      │
│    ── 状态更新: state.update({"messages": [...]})                                        │
│                                                                                           │
│  路由判断: makeToolsToModelEdge(state)                                                    │
│    │                                                                                      │
│    ├── 检查工具是否要求直接返回                                                             │
│    │                                                                                      │
│    └── 不要求直接返回 → 下一个节点: loopEntryNode (回到模型)                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘
    │
    ▼
─────────────────────────────────────────────────────────────────────────────────────────┐
│  第三轮循环                                                                                │
│                                                                                           │
│  执行节点: AGENT_MODEL                                                                    │
│    │                                                                                      │
│    ├── 调用 LLM: chatModel.call(messages)  (包含之前的消息历史)                            │
│    │                                                                                      │
│    ├── 返回: {"messages": [AssistantMessage]}  (无工具调用)                               │
│    │                                                                                      │
│    └── 状态更新: state.update({"messages": [...]})                                        │
│                                                                                           │
│  路由判断: makeModelToTools(state)                                                        │
│    │                                                                                      │
│    ├── 检查最后一条消息是否有工具调用                                                       │
│    │                                                                                      │
│    ── 无工具调用 → 下一个节点: END (结束)                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘
    │
    ▼
最终状态: OverAllState
    │
    ├── messages: [
    │       HumanMessage("用户问题"),
    │       AssistantMessage(有工具调用),
    │       ToolResponseMessage(工具结果),
    │       AssistantMessage(最终回答)
    │   ]
    │
    └── 其他状态字段...
```

## 关键类说明

| 类名 | 说明 |
|------|------|
| `CompiledGraph` | 编译后的图，包含所有节点和边的定义 |
| `OverAllState` | 图的全局状态，存储所有键值对 |
| `NodeOutput` | 节点输出，包含节点名称和当前状态 |
| `Flux<NodeOutput>` | 响应式流，包含图执行过程中每个节点的输出 |
| `Mono<T>` | 响应式流，包含单个元素或为空 |
| `RunnableConfig` | 运行配置，包含线程ID、检查点等 |
| `KeyStrategy` | 键策略，定义状态中各个键的更新方式（覆盖、追加、合并等） |

## 与 stream 方法的区别

| 特性 | invoke | stream |
|------|--------|--------|
| 返回类型 | `Optional<OverAllState>` | `Flux<NodeOutput>` |
| 执行方式 | 同步阻塞 | 异步流式 |
| 结果获取 | 只返回最终状态 | 返回每个节点的输出 |
| 适用场景 | 只需要最终结果 | 需要实时获取中间状态（如 SSE 流式输出） |

## 使用示例

```java
// 创建输入
Map<String, Object> inputs = Map.of("input", "你好，请介绍一下自己");

// 创建配置
RunnableConfig config = RunnableConfig.builder()
        .threadId("session-123")
        .build();

// 同步执行并获取最终状态
Optional<OverAllState> result = compiledGraph.invoke(inputs, config);

// 处理结果
result.ifPresent(state -> {
    List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
    Message lastMessage = messages.get(messages.size() - 1);
    System.out.println("最终回答: " + lastMessage.getText());
});
```