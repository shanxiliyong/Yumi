HumanInTheLoopHook: 响应中断与恢复
OverAllState（全局状态）是 Spring AI Alibaba Graph 框架中的核心数据中枢

HumanInTheLoopHook
![img_3.png](img_3.png)

skill 拼接玩的 prompt 是怎么样的？如何查看

skill: SkillsAgentHook  registry
cli 命令:ShellToolAgentHook

| Agent 类型      | 核心机制     | 执行方式 | 适用场景             | 是否多选 | 是否循环 | 是否并行 |
| --------------- | ------------ | -------- | -------------------- | -------- | -------- | -------- |
| LlmRoutingAgent | LLM 意图识别 | 单选     | 任务分发、意图路由   | ❌       | ❌       | ❌       |
| LoopAgent       | 条件判断     | 循环迭代 | 内容打磨、质量审核   | ❌       | ✅       | ❌       |
| ParallelAgent   | 并发调度     | 并行执行 | 多源采集、多规则校验 | ✅       | ❌       | ✅       |
| SequentialAgent | 顺序依赖     | 串行执行 | 流程化任务、分步处理 | ❌       | ❌       | ❌       |
| SupervisorAgent | 全局协调     | 动态调度 | 复杂项目、多角色协作 | ✅       | ✅/❌    | ✅/❌    |

```
Agent (顶层抽象根)
└─ BaseAgent (通用图/状态能力抽象)
├─ ReactAgent (单智能体ReAct工具调用，无ToolCallAgent)
└─ FlowAgent (多智能体编排抽象父类)
    ├─ LlmRoutingAgent
    ├─ LoopAgent
    ├─ ParallelAgent
    ├─ SequentialAgent
    └─ SupervisorAgent
```

SupervisorAgent  废弃 ，使用Agent As Tool代替

用户提问 → 思考 → 执行->观察？




图(Graph)：节点（Node）+边(Edge)

什么时候生成 

状态(State)：共享的上下文状态

AsyncNodeActionWithConfig
