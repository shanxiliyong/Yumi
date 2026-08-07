package yumi.agent;

import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool2;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yumi.common.JackJsonUtil;
import yumi.common.ThreadPoolUtil;
import yumi.common.YumiContext;
import yumi.entity.DigitalHumanEntity;
import yumi.entity.ToolEntity;
import yumi.hook.LogModelHook;
import yumi.mapper.DigitalHumanMapper;
import yumi.service.SkillService;
import yumi.service.ToolService;
import yumi.skill.DatabaseSkillRegistry;
import yumi.tool.RpcToolCallback;
import yumi.tool.SystemToolRegistry;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class AgentBuilderService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private SkillService skillService;

    @Autowired
    private RpcToolCallback rpcToolCallback;

    @Autowired
    private ToolService toolService;

    @Autowired
    private SystemToolRegistry systemToolRegistry;

    @Autowired
    private DigitalHumanMapper digitalHumanMapper;

    @Autowired
    private MysqlSaver saver;



    /**
     * 构建Agent实例
     * 根据YumiContext中的数字人配置，创建并返回一个ReactAgent实例
     * 包括配置基础信息、工具、Hooks、并发参数等
     *
     * @param context 包含数字人配置的上下文对象
     * @return 构建完成的ReactAgent实例
     * @throws IllegalArgumentException 当数字人配置为空时抛出
     */
    public ReactAgent buildAgent(YumiContext context) {
        DigitalHumanEntity dh = context.getDh();
        if (dh == null) {
            throw new IllegalArgumentException("数字人配置不能为空");
        }

        // 初始化Agent基础配置：名称、模型、系统提示词、检查点保存器等
        Builder agentBuilder = ReactAgent.builder()
                .name(dh.getCode())
                .model(chatModel)
                .enableLogging(false)
                .toolExecutionTimeout(Duration.ofSeconds(10))
                .systemPrompt(dh.getSystemPrompt())
                .saver(saver);

        // 并发相关配置：设置线程池、启用并行工具执行、最大并行工具数
        agentBuilder.executor(ThreadPoolUtil.initThreadPool(5)).parallelToolExecution(true).maxParallelTools(5);

        // 子Agent配置输入Schema：如果当前Agent是子Agent，则从配置中提取inputSchema
        if (dh.getAgentType().equals("child") && StringUtils.isNotBlank(dh.getConfig())) {
            Map<String, Map> map = JackJsonUtil.toMap(dh.getConfig(), String.class, Map.class);
            if (map != null) {
                Map inputSchema = map.get("inputSchema");
                if (inputSchema != null) {
                    agentBuilder.inputSchema(JackJsonUtil.toJsonStr(inputSchema));
                }
            }
        }


        // 加载配置工具
        List<ToolCallback> allTools = new ArrayList<>();


        // 加载数字人配置中指定的工具
        List<ToolCallback> tools = loadConfiguredTools(dh);
        log.info("load loadConfiguredTools tools: {}", tools.size());
        allTools.addAll(tools);

        // 父Agent动态加载子Agent作为工具
        List<ToolCallback> subAgentTools = loadSubAgentsAsTools(context);
        log.info("load sub-agent tools: {}", subAgentTools.size());
        allTools.addAll(subAgentTools);


        // 将所有工具注册到Agent
        if (!allTools.isEmpty()) {
            agentBuilder.tools(allTools);
        }

        // 加载并注册Hooks
        List<Hook> allHooks = initHook(dh);
        log.info("load initHook hooks: {}", allHooks.size());

        if (!allHooks.isEmpty()) {
            agentBuilder.hooks(allHooks);
        }


        // 构建并返回Agent实例
        ReactAgent agent = agentBuilder.build();
        log.info("初始化Agent完成: name={}, agent={}, ", dh.getName(), JackJsonUtil.toJsonStr(agent));
        return agent;
    }
    /**
     * 初始化Hooks列表
     * 为Agent配置各种Hooks，包括日志Hook、技能Hook、工具Hook等
     *
     * @param dh 数字人实体对象
     * @return 包含所有Hooks的列表
     */
    @NotNull
    private List<Hook> initHook(DigitalHumanEntity dh) {
        List<Hook> hooks = new ArrayList<>();
        // 添加日志模型Hook，用于记录模型交互日志
        hooks.add(new LogModelHook());
//        hooks.add(new LogAgentHook());
        
        // 加载配置的技能Hook
        SkillsAgentHook skillsAgentHook = loadConfiguredSkill(dh);
        log.info("skillsAgentHook loaded: {}", skillsAgentHook);
        if (skillsAgentHook != null) {
            hooks.add(skillsAgentHook);
        }
        
        // 加载配置的工具Hooks
        List<Hook> toolHooks = loadConfiguredToolHooks(dh);
        log.info("toolHooks loaded: {}", toolHooks);
        hooks.addAll(toolHooks);
        return hooks;
    }

    /**
     * 加载配置的技能
     * 根据数字人配置中的skillIds，从数据库加载对应的技能并创建SkillsAgentHook
     *
     * @param dh 数字人实体对象
     * @return 配置好的SkillsAgentHook，如果没有配置技能则返回null
     */
    private SkillsAgentHook loadConfiguredSkill(DigitalHumanEntity dh) {
        SkillsAgentHook skillHook = null;
        // 解析技能ID列表
        List<Long> skillIds = parseSkillIds(dh.getSkillIds());
        if (CollectionUtils.isNotEmpty(skillIds)) {
            // 构建数据库技能注册器，用于从数据库加载技能
            SkillRegistry registry = DatabaseSkillRegistry.builder()
                    .skillService(skillService)
                    .skillIds(skillIds)
                    .registryId(dh.getName())
                    .build();
            // 只有当注册器中有可用技能时才创建Hook
            if (!registry.listAll().isEmpty()) {
                skillHook = SkillsAgentHook.builder()
                        .skillRegistry(registry)
                        .build();
            }
        }
        return skillHook;
    }

    /**
     * 加载配置的工具
     * 根据数字人配置中的toolIds，从数据库加载工具并转换为ToolCallback
     * 支持system和rpc两种类型的工具
     *
     * @param dh 数字人实体对象
     * @return 包含所有成功加载的工具回调列表
     */
    private List<ToolCallback> loadConfiguredTools(DigitalHumanEntity dh) {
        // 解析工具ID列表
        List<Long> toolIds = parseToolIds(dh.getToolIds());
        if (toolIds.isEmpty()) {
            log.info("数字人 [{}] 未配置任何工具", dh.getName());
            return new ArrayList<>();
        }

        // 从数据库查询工具实体
        List<ToolEntity> configuredTools = toolService.listByIds(toolIds);
        if (configuredTools.isEmpty()) {
            log.warn("数字人 [{}] 配置的工具ID未找到: {}", dh.getName(), toolIds);
            return new ArrayList<>();
        }

        List<ToolCallback> callbacks = new ArrayList<>();
        // 遍历工具列表，根据类型创建对应的ToolCallback
        for (ToolEntity tool : configuredTools) {
            try {
                String type = tool.getType();
                if ("system".equals(type)) {
                    // 系统类型工具：从系统注册器中创建
                    if (systemToolRegistry.supports(tool.getName())) {
                        callbacks.add(systemToolRegistry.createTool(tool));
                    } else {
                        log.warn("系统工具未注册: name={}", tool.getName());
                    }
                } else if ("rpc".equals(type)) {
                    // RPC类型工具：通过RpcToolCallback批量创建
                    callbacks.addAll(rpcToolCallback.createRpcTools(List.of(tool)));
                } else {
                    log.warn("未知的工具类型: type={}, name={}", type, tool.getName());
                }
            } catch (Exception e) {
                log.error("加载工具失败: name={}, type={}", tool.getName(), tool.getType(), e);
            }
        }

        log.info("数字人 [{}] 加载工具: {} 个", dh.getName(), callbacks.size());
        return callbacks;
    }


    /**
     * 加载配置的工具Hooks
     * 根据数字人配置中的工具，创建对应的Hook
     * 特殊处理：SUMMARIZATION工具创建SummarizationHook，SHELL工具创建ShellToolAgentHook和HumanInTheLoopHook
     *
     * @param dh 数字人实体对象
     * @return 包含所有工具Hooks的列表
     */
    private List<Hook> loadConfiguredToolHooks(DigitalHumanEntity dh) {
        // 解析工具ID列表
        List<Long> toolIds = parseToolIds(dh.getToolIds());
        if (toolIds.isEmpty()) {
            log.info("数字人 [{}] 未配置任何工具", dh.getName());
            return new ArrayList<>();
        }

        // 从数据库查询工具实体
        List<ToolEntity> configuredTools = toolService.listByIds(toolIds);
        if (configuredTools.isEmpty()) {
            log.warn("数字人 [{}] 配置的工具ID未找到: {}", dh.getName(), toolIds);
            return new ArrayList<>();
        }
        List<Hook> hooks = new ArrayList<>();
        // 遍历工具列表，为特定工具创建对应的Hook
        for (ToolEntity tool : configuredTools) {

            String type = tool.getType();
            // 如果是SUMMARIZATION工具，创建摘要Hook用于控制上下文长度
            if ("system".equals(type) && tool.getName().equals(SystemToolRegistry.DEFAULT_SUMMARIZATION_TOOL_NAME)) {
                SummarizationHook summarizationHook = SummarizationHook.builder()
                        .model(chatModel)
                        .maxTokensBeforeSummary(3000)  // 3000 tokens后触发摘要
                        .messagesToKeep(8)              // 保留最近8条消息
                        .build();
                hooks.add(summarizationHook);
            } 
            // 如果是SHELL工具，创建Shell执行Hook和人工确认Hook
            else if ("system".equals(type) && tool.getName().equals(SystemToolRegistry.DEFAULT_SHELL_TOOL_NAME)) {
                // 创建Shell工具执行Hook
                ShellToolAgentHook shellHook = ShellToolAgentHook.builder()
                        .shellTool2(ShellTool2.builder(System.getProperty("user.dir")).build())
                        .shellToolName(tool.getName())
                        .build();
                hooks.add(shellHook);

                // 创建人工确认Hook，执行shell命令前需要用户确认
                HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
                        .approvalOn("shell", ToolConfig.builder()
                                .description("请确认执行shell命令")
                                .build())
                        .build();
                hooks.add(humanInTheLoopHook);
            }

        }

        return hooks;
    }

    /**
     * 动态加载子Agent作为工具
     * 查询条件: parent_code = 父Agent的code, multi_agent_enabled = 1, agent_type = 'child'
     * 该方法会将所有符合条件的子Agent递归创建，并包装为ToolCallback供父Agent调用
     *
     * @param context 包含父数字人配置的上下文对象
     * @return 包含所有子Agent工具回调的列表
     */
    private List<ToolCallback> loadSubAgentsAsTools(YumiContext context) {

        List<ToolCallback> subAgentTools = new ArrayList<>();
        DigitalHumanEntity parentDh = context.getDh();
        
        // 验证是否为父Agent类型
        if (!parentDh.getAgentType().equals("parent")) {
            log.warn("数字人 [{}] 不是父Agent，无法加载子Agent", parentDh.getName());
            return subAgentTools;
        }

        // 验证父Agent的code是否有效
        if (parentDh.getCode() == null || parentDh.getCode().trim().isEmpty()) {
            log.warn("父数字人 [{}] 的 code 为空，无法加载子Agent", parentDh.getName());
            return subAgentTools;
        }
        
        // 验证是否启用了多Agent功能
        if (parentDh.getMultiAgentEnabled() == null || parentDh.getMultiAgentEnabled() == 0) {
            log.info("数字人 [{}] 未启用多Agent功能，无法加载子Agent", parentDh.getName());
            return subAgentTools;
        }

        // 查询子Agent列表：根据父Agent的code查询所有子Agent
        List<DigitalHumanEntity> subAgents = digitalHumanMapper.selectChildrenByParentCode(parentDh.getCode());
        if (subAgents == null || subAgents.isEmpty()) {
            log.info("数字人 [{}] 没有配置子Agent", parentDh.getName());
            return subAgentTools;
        }

        // 遍历子Agent列表，递归创建每个子Agent并包装为工具
        for (DigitalHumanEntity subAgent : subAgents) {
            try {
                // 创建子Agent的上下文，并设置父Agent引用
                YumiContext subContext = new YumiContext();
                subContext.setDh(subAgent);
                subContext.setParentDh(parentDh);

                // 递归创建子Agent实例
                ReactAgent childAgent = buildAgent(subContext);

                // 使用 AgentTool.getFunctionToolCallback 将子Agent包装为工具
                // 框架会自动将父Agent的RunnableConfig（含threadId）传递给子Agent，无需手动构建config
                ToolCallback toolCallback = AgentTool.getFunctionToolCallback(childAgent);

                subAgentTools.add(toolCallback);
                log.info("加载子Agent工具: name={}, code={}", subAgent.getName(), subAgent.getCode());
            } catch (Exception e) {
                log.error("加载子Agent失败: name={}, code={}", subAgent.getName(), subAgent.getCode(), e);
            }
        }

        log.info("数字人 [{}] 加载子Agent工具: {} 个", parentDh.getName(), subAgentTools.size());
        return subAgentTools;
    }

    /**
     * 清理code作为工具名称（移除特殊字符）
     * 将code中的特殊字符替换为下划线，并转换为小写，确保符合工具命名规范
     *
     * @param code 原始code字符串
     * @return 清理后的工具名称
     */
    private String sanitizeToolName(String code) {
        if (code == null) return "unknown_agent";
        // 移除所有非字母、数字、下划线的字符，并转换为小写
        return code.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    /**
     * 构建子Agent工具描述
     * 根据子Agent的名称和描述生成工具描述信息，用于父Agent了解子Agent的功能
     *
     * @param subAgent 子Agent实体对象
     * @return 格式化的工具描述字符串
     */
    private String buildSubAgentDescription(DigitalHumanEntity subAgent) {
        StringBuilder desc = new StringBuilder();
        desc.append("子Agent: ").append(subAgent.getName());
        // 如果子Agent有描述信息，则追加到描述中
        if (subAgent.getDescription() != null && !subAgent.getDescription().isEmpty()) {
            desc.append(" - ").append(subAgent.getDescription());
        }
        return desc.toString();
    }

    /**
     * 解析工具ID列表
     * 从JSON字符串中解析出工具ID列表，用于从数据库加载工具配置
     *
     * @param toolIdsJson JSON格式的工具ID列表字符串
     * @return 工具ID列表，解析失败时返回空列表
     */
    private List<Long> parseToolIds(String toolIdsJson) {
        if (toolIdsJson == null || toolIdsJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(toolIdsJson, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("解析 toolIds 失败: {}", toolIdsJson, e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析技能ID列表
     * 从JSON字符串中解析出技能ID列表，用于从数据库加载技能配置
     *
     * @param skillIdsJson JSON格式的技能ID列表字符串
     * @return 技能ID列表，解析失败时返回空列表
     */
    private List<Long> parseSkillIds(String skillIdsJson) {
        if (skillIdsJson == null || skillIdsJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(skillIdsJson, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("解析 skillIds 失败: {}", skillIdsJson, e);
            return Collections.emptyList();
        }
    }
}