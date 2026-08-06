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


    public ReactAgent buildAgent(YumiContext context) {
        DigitalHumanEntity dh = context.getDh();
        if (dh == null) {
            throw new IllegalArgumentException("数字人配置不能为空");
        }

        Builder agentBuilder = ReactAgent.builder()
                .name(dh.getCode())
                .model(chatModel)
                .enableLogging(false)
                .toolExecutionTimeout(Duration.ofSeconds(10))
                .systemPrompt(dh.getSystemPrompt())
                .saver(saver);

        // 并发相关配置
        agentBuilder.executor(ThreadPoolUtil.initThreadPool(5)).parallelToolExecution(true).maxParallelTools(5);

        // 子Agent配置输入Schema
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


        List<ToolCallback> tools = loadConfiguredTools(dh);
        log.info("load loadConfiguredTools tools: {}", tools.size());
        allTools.addAll(tools);

        // 父Agent动态加载子Agent作为工具
        List<ToolCallback> subAgentTools = loadSubAgentsAsTools(context);
        log.info("load sub-agent tools: {}", subAgentTools.size());
        allTools.addAll(subAgentTools);


        if (!allTools.isEmpty()) {
            agentBuilder.tools(allTools);
        }

        // 加载 Hooks
        List<Hook> allHooks = initHook(dh);
        log.info("load initHook hooks: {}", allHooks.size());

        if (!allHooks.isEmpty()) {
            agentBuilder.hooks(allHooks);
        }


        ReactAgent agent = agentBuilder.build();
        log.info("初始化Agent完成: name={}, agent={}, ", dh.getName(), JackJsonUtil.toJsonStr(agent));
        return agent;
    }

    @NotNull
    private List<Hook> initHook(DigitalHumanEntity dh) {
        List<Hook> hooks = new ArrayList<>();
        hooks.add(new LogModelHook());
//        hooks.add(new LogAgentHook());
        SkillsAgentHook skillsAgentHook = loadConfiguredSkill(dh);
        log.info("skillsAgentHook loaded: {}", skillsAgentHook);
        if (skillsAgentHook != null) {
            hooks.add(skillsAgentHook);
        }
        List<Hook> toolHooks = loadConfiguredToolHooks(dh);
        log.info("toolHooks loaded: {}", toolHooks);
        hooks.addAll(toolHooks);
        return hooks;
    }

    private SkillsAgentHook loadConfiguredSkill(DigitalHumanEntity dh) {
        SkillsAgentHook skillHook = null;
        List<Long> skillIds = parseSkillIds(dh.getSkillIds());
        if (CollectionUtils.isNotEmpty(skillIds)) {
            SkillRegistry registry = DatabaseSkillRegistry.builder()
                    .skillService(skillService)
                    .skillIds(skillIds)
                    .registryId(dh.getName())
                    .build();
            if (!registry.listAll().isEmpty()) {
                skillHook = SkillsAgentHook.builder()
                        .skillRegistry(registry)
                        .build();
            }
        }
        return skillHook;
    }

    private List<ToolCallback> loadConfiguredTools(DigitalHumanEntity dh) {
        List<Long> toolIds = parseToolIds(dh.getToolIds());
        if (toolIds.isEmpty()) {
            log.info("数字人 [{}] 未配置任何工具", dh.getName());
            return new ArrayList<>();
        }

        List<ToolEntity> configuredTools = toolService.listByIds(toolIds);
        if (configuredTools.isEmpty()) {
            log.warn("数字人 [{}] 配置的工具ID未找到: {}", dh.getName(), toolIds);
            return new ArrayList<>();
        }

        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolEntity tool : configuredTools) {
            try {
                String type = tool.getType();
                if ("system".equals(type)) {
                    if (systemToolRegistry.supports(tool.getName())) {
                        callbacks.add(systemToolRegistry.createTool(tool));
                    } else {
                        log.warn("系统工具未注册: name={}", tool.getName());
                    }
                } else if ("rpc".equals(type)) {
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


    private List<Hook> loadConfiguredToolHooks(DigitalHumanEntity dh) {
        List<Long> toolIds = parseToolIds(dh.getToolIds());
        if (toolIds.isEmpty()) {
            log.info("数字人 [{}] 未配置任何工具", dh.getName());
            return new ArrayList<>();
        }

        List<ToolEntity> configuredTools = toolService.listByIds(toolIds);
        if (configuredTools.isEmpty()) {
            log.warn("数字人 [{}] 配置的工具ID未找到: {}", dh.getName(), toolIds);
            return new ArrayList<>();
        }
        List<Hook> hooks = new ArrayList<>();
        for (ToolEntity tool : configuredTools) {

            String type = tool.getType();
            if ("system".equals(type) && tool.getName().equals(SystemToolRegistry.DEFAULT_SUMMARIZATION_TOOL_NAME)) {
                SummarizationHook summarizationHook = SummarizationHook.builder()
                        .model(chatModel)
                        .maxTokensBeforeSummary(3000)
                        .messagesToKeep(8)
                        .build();
                hooks.add(summarizationHook);
            } else if ("system".equals(type) && tool.getName().equals(SystemToolRegistry.DEFAULT_SHELL_TOOL_NAME)) {
                ShellToolAgentHook shellHook = ShellToolAgentHook.builder()
                        .shellTool2(ShellTool2.builder(System.getProperty("user.dir")).build())
                        .shellToolName(tool.getName())
                        .build();
                hooks.add(shellHook);


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
     */
    private List<ToolCallback> loadSubAgentsAsTools(YumiContext context) {

        List<ToolCallback> subAgentTools = new ArrayList<>();
        DigitalHumanEntity parentDh = context.getDh();
        if (!parentDh.getAgentType().equals("parent")) {
            log.warn("数字人 [{}] 不是父Agent，无法加载子Agent", parentDh.getName());
            return subAgentTools;
        }

        if (parentDh.getCode() == null || parentDh.getCode().trim().isEmpty()) {
            log.warn("父数字人 [{}] 的 code 为空，无法加载子Agent", parentDh.getName());
            return subAgentTools;
        }
        if (parentDh.getMultiAgentEnabled() == null || parentDh.getMultiAgentEnabled() == 0) {
            log.info("数字人 [{}] 未启用多Agent功能，无法加载子Agent", parentDh.getName());
            return subAgentTools;
        }

        // 查询子Agent列表
        List<DigitalHumanEntity> subAgents = digitalHumanMapper.selectChildrenByParentCode(parentDh.getCode());
        if (subAgents == null || subAgents.isEmpty()) {
            log.info("数字人 [{}] 没有配置子Agent", parentDh.getName());
            return subAgentTools;
        }

        for (DigitalHumanEntity subAgent : subAgents) {
            try {
                YumiContext subContext = new YumiContext();
                subContext.setDh(subAgent);
                subContext.setParentDh(parentDh);

                // 递归创建子Agent实例
                ReactAgent childAgent = buildAgent(subContext);

                // 使用 AgentTool.getFunctionToolCallback 包装子Agent为工具
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
     */
    private String sanitizeToolName(String code) {
        if (code == null) return "unknown_agent";
        return code.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    /**
     * 构建子Agent工具描述
     */
    private String buildSubAgentDescription(DigitalHumanEntity subAgent) {
        StringBuilder desc = new StringBuilder();
        desc.append("子Agent: ").append(subAgent.getName());
        if (subAgent.getDescription() != null && !subAgent.getDescription().isEmpty()) {
            desc.append(" - ").append(subAgent.getDescription());
        }
        return desc.toString();
    }

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