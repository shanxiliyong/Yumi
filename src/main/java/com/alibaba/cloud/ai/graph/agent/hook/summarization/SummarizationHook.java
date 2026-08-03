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
package com.alibaba.cloud.ai.graph.agent.hook.summarization;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.TokenCounter;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hook that summarizes conversation history when token limits are approached.
 *
 * This hook monitors message token counts and automatically summarizes older
 * messages when a threshold is reached, preserving the first user message and
 * recent messages to maintain context continuity.
 *
 * Example:
 * SummarizationHook summarizer = SummarizationHook.builder()
 *     .model(chatModel)
 *     .maxTokensBeforeSummary(4000)
 *     .messagesToKeep(20)
 *     .keepFirstUserMessage(true)  // Default: true
 *     .build();
 */
@HookPositions({HookPosition.BEFORE_MODEL})
public class SummarizationHook extends MessagesModelHook {

    private static final Logger log = LoggerFactory.getLogger(SummarizationHook.class);

    private static final String DEFAULT_SUMMARY_PROMPT =
            "<role>\nContext Extraction Assistant\n</role>\n\n" +
                    "<primary_objective>\n" +
                    "Your sole objective in this task is to extract the highest quality/most relevant context " +
                    "from the conversation history below.\n</primary_objective>\n\n" +
                    "<instructions>\n" +
                    "The conversation history below will be replaced with the context you extract in this step. " +
                    "Extract and record all of the most important context from the conversation history.\n" +
                    "Respond ONLY with the extracted context. Do not include any additional information.\n" +
                    "</instructions>\n\n" +
                    "<messages>\nMessages to summarize:\n%s\n</messages>";

    private static final String SUMMARY_PREFIX = "## Previous conversation summary:";
    private static final int DEFAULT_MESSAGES_TO_KEEP = 20;
    private static final int SEARCH_RANGE_FOR_TOOL_PAIRS = 5;
    private static final boolean DEFAULT_KEEP_FIRST_USER_MESSAGE = true;

    private final ChatModel model;
    private final Integer maxTokensBeforeSummary;
    private final int messagesToKeep;
    private final TokenCounter tokenCounter;
    private final String summaryPrompt;
    private final String summaryPrefix;
    private final boolean keepFirstUserMessage;

    private SummarizationHook(Builder builder) {
        this.model = builder.model;
        this.maxTokensBeforeSummary = builder.maxTokensBeforeSummary;
        this.messagesToKeep = builder.messagesToKeep;
        this.tokenCounter = builder.tokenCounter;
        this.summaryPrompt = builder.summaryPrompt;
        this.summaryPrefix = builder.summaryPrefix;
        this.keepFirstUserMessage = builder.keepFirstUserMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        // ========== 步骤 1：检查是否启用总结功能 ==========
        // 如果 maxTokensBeforeSummary 为 null，说明未配置总结阈值，直接返回原始消息列表
        if (maxTokensBeforeSummary == null) {
            return new AgentCommand(previousMessages);
        }

        // ========== 步骤 2：计算当前消息的总 Token 数 ==========
        // 使用 TokenCounter 统计所有历史消息的 Token 总数
        int totalTokens = tokenCounter.countTokens(previousMessages);

        // ========== 步骤 3：判断是否需要触发总结 ==========
        // 如果总 Token 数未达到阈值，无需总结，直接返回原始消息
        if (totalTokens < maxTokensBeforeSummary) {
            return new AgentCommand(previousMessages);
        }

        // Token 数超过阈值，记录日志并触发总结流程
        log.info("Token count {} exceeds threshold {}, triggering summarization",
                totalTokens, maxTokensBeforeSummary);

        // ========== 步骤 4：寻找安全的截断点 ==========
        // 截断点决定了哪些消息被总结、哪些消息保留
        // findSafeCutoff 会确保不会将 AI 的工具调用消息和对应的工具响应消息分开
        int cutoffIndex = findSafeCutoff(previousMessages);

        // 如果找不到安全的截断点（消息太少），放弃总结，返回原始消息
        if (cutoffIndex <= 0) {
            log.warn("Cannot find safe cutoff point for summarization");
            return new AgentCommand(previousMessages);
        }

        // ========== 步骤 5：提取第一条用户消息（可选保留） ==========
        // 如果 keepFirstUserMessage 为 true，保留对话的第一条用户消息
        // 这样可以确保大模型始终知道对话的初始意图
        UserMessage firstUserMessage = null;
        if (keepFirstUserMessage) {
            for (Message msg : previousMessages) {
                if (msg instanceof UserMessage) {
                    firstUserMessage = (UserMessage) msg;
                    break;
                }
            }
        }

        // ========== 步骤 6：收集需要总结的消息 ==========
        // 将截断点之前的消息（除第一条用户消息外）收集到待总结列表
        List<Message> toSummarize = new ArrayList<>();
        for (int i = 0; i < cutoffIndex; i++) {
            Message msg = previousMessages.get(i);
            if (msg != firstUserMessage) {
                toSummarize.add(msg);
            }
        }

        // ========== 步骤 7：调用大模型生成总结 ==========
        // 将待总结的消息发送给大模型，生成一段简洁的对话摘要
        String summary = createSummary(toSummarize);

        // 将总结内容包装为 SystemMessage，添加前缀标识
        SystemMessage summaryMessage = new SystemMessage(summaryPrefix + "\n" + summary);

        // ========== 步骤 8：收集截断点之后的最近消息 ==========
        // 保留最近的消息（不被总结），确保上下文连贯性
        List<Message> recentMessages = new ArrayList<>();
        for (int i = cutoffIndex; i < previousMessages.size(); i++) {
            recentMessages.add(previousMessages.get(i));
        }

        // ========== 步骤 9：组装新的消息列表 ==========
        // 新消息列表的结构：[第一条用户消息] + [总结消息] + [最近消息]
        List<Message> newMessages = new ArrayList<>();
        if (firstUserMessage != null) {
            newMessages.add(firstUserMessage);  // 保留初始用户意图
        }
        newMessages.add(summaryMessage);        // 插入总结
        newMessages.addAll(recentMessages);     // 追加最近消息

        // 记录总结结果
        if (firstUserMessage != null) {
            log.info("Summarized {} messages, keeping {} recent messages (First UserMessage preserved)",
                    toSummarize.size(), recentMessages.size());
        } else {
            log.info("Summarized {} messages, keeping {} recent messages",
                    toSummarize.size(), recentMessages.size());
        }

        // ========== 步骤 10：返回新的消息列表 ==========
        // UpdatePolicy.REPLACE 表示用新消息列表完全替换旧列表
        return new AgentCommand(newMessages, UpdatePolicy.REPLACE);
    }

    /**
     * 寻找安全的截断点，确保不会拆散 AI 工具调用和对应的工具响应消息对。
     *
     * 执行逻辑：
     * 1. 如果消息总数 <= 需要保留的消息数，说明消息太少，无需截断，返回 0
     * 2. 计算目标截断点：总消息数 - 需要保留的消息数
     * 3. 从目标截断点向前搜索，找到第一个安全的截断位置
     * 4. 如果找不到安全位置，返回 0（放弃截断）
     *
     * @param messages 完整的消息列表
     * @return 安全截断点的索引，0 表示无法找到安全截断点
     */
    private int findSafeCutoff(List<Message> messages) {
        // 消息太少，无需截断
        if (messages.size() <= messagesToKeep) {
            return 0;
        }

        // 计算理想截断点：保留最后 messagesToKeep 条消息
        int targetCutoff = messages.size() - messagesToKeep;

        // 从目标截断点向前搜索，找到第一个安全的截断位置
        for (int i = targetCutoff; i >= 0; i--) {
            if (isSafeCutoffPoint(messages, i)) {
                return i;
            }
        }

        // 找不到安全位置，返回 0
        return 0;
    }

    /**
     * 检查在指定索引处截断是否会拆散 AI 工具调用和对应的工具响应消息对。
     *
     * 执行逻辑：
     * 1. 在截断点前后一定范围内（SEARCH_RANGE_FOR_TOOL_PAIRS）搜索
     * 2. 查找包含工具调用的 AI 消息
     * 3. 检查该 AI 消息的工具调用 ID 和对应的工具响应是否被截断点分开
     * 4. 如果任何工具对被分开，返回 false（不安全）；否则返回 true（安全）
     *
     * @param messages 完整的消息列表
     * @param cutoffIndex 待检查的截断点索引
     * @return true 表示可以安全截断，false 表示会拆散工具对
     */
    private boolean isSafeCutoffPoint(List<Message> messages, int cutoffIndex) {
        // 截断点超出消息范围，视为安全
        if (cutoffIndex >= messages.size()) {
            return true;
        }

        // 在截断点前后一定范围内搜索工具调用
        int searchStart = Math.max(0, cutoffIndex - SEARCH_RANGE_FOR_TOOL_PAIRS);
        int searchEnd = Math.min(messages.size(), cutoffIndex + SEARCH_RANGE_FOR_TOOL_PAIRS);

        // 遍历搜索范围内的每条消息
        for (int i = searchStart; i < searchEnd; i++) {
            // 跳过不包含工具调用的消息
            if (!hasToolCalls(messages.get(i))) {
                continue;
            }

            // 提取 AI 消息中的工具调用 ID
            AssistantMessage aiMessage = (AssistantMessage) messages.get(i);
            Set<String> toolCallIds = extractToolCallIds(aiMessage);

            // 检查截断点是否会将该 AI 消息与其工具响应分开
            if (cutoffSeparatesToolPair(messages, i, cutoffIndex, toolCallIds)) {
                return false;  // 会拆散工具对，不安全
            }
        }

        return true;  // 所有工具对都完整，安全
    }

    /**
     * 检查消息是否为包含工具调用的 AI 消息。
     *
     * @param message 待检查的消息
     * @return true 表示是包含工具调用的 AI 消息
     */
    private boolean hasToolCalls(Message message) {
        return message instanceof AssistantMessage assistantMessage && !assistantMessage.getToolCalls().isEmpty();
    }

    /**
     * 从 AI 消息中提取所有工具调用的 ID。
     *
     * @param aiMessage 包含工具调用的 AI 消息
     * @return 工具调用 ID 集合
     */
    private Set<String> extractToolCallIds(AssistantMessage aiMessage) {
        Set<String> toolCallIds = new HashSet<>();
        for (AssistantMessage.ToolCall toolCall : aiMessage.getToolCalls()) {
            String callId = toolCall.id();
            toolCallIds.add(callId);
        }
        return toolCallIds;
    }

    /**
     * 检查截断点是否会将 AI 工具调用消息与其对应的工具响应消息分开。
     *
     * 执行逻辑：
     * 1. 从 AI 消息的下一条消息开始遍历
     * 2. 查找工具响应消息（ToolResponseMessage）
     * 3. 检查工具响应的 ID 是否匹配 AI 消息中的工具调用 ID
     * 4. 如果 AI 消息和工具响应分别在截断点的两侧，说明被分开了
     *
     * @param messages 完整的消息列表
     * @param aiMessageIndex AI 工具调用消息的索引
     * @param cutoffIndex 截断点索引
     * @param toolCallIds AI 消息中的工具调用 ID 集合
     * @return true 表示截断点会拆散工具对，false 表示工具对完整
     */
    private boolean cutoffSeparatesToolPair(
            List<Message> messages,
            int aiMessageIndex,
            int cutoffIndex,
            Set<String> toolCallIds) {
        // 从 AI 消息的下一条开始查找对应的工具响应
        for (int j = aiMessageIndex + 1; j < messages.size(); j++) {
            Message message = messages.get(j);
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                // 检查工具响应消息中的每个响应
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    // 如果响应 ID 匹配工具调用 ID
                    if (toolCallIds.contains(response.id())) {
                        // 判断 AI 消息和工具响应是否分别在截断点两侧
                        boolean aiBeforeCutoff = aiMessageIndex < cutoffIndex;
                        boolean toolBeforeCutoff = j < cutoffIndex;
                        // 一侧在截断点前，一侧在截断点后 → 被分开了
                        if (aiBeforeCutoff != toolBeforeCutoff) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;  // 工具对完整，未被分开
    }

    /**
     * 调用大模型生成对话总结。
     *
     * 执行逻辑：
     * 1. 如果消息列表为空，返回默认提示
     * 2. 将所有消息格式化为 "角色: 内容" 的文本格式
     * 3. 将格式化后的文本填入总结提示模板
     * 4. 调用大模型生成总结
     * 5. 如果调用失败，返回错误信息
     *
     * @param messages 需要总结的消息列表
     * @return 生成的总结文本
     */
    private String createSummary(List<Message> messages) {
        // 空消息列表，返回默认提示
        if (messages.isEmpty()) {
            return "No previous conversation.";
        }

        // 将消息格式化为 "角色: 内容" 的文本
        StringBuilder messageText = new StringBuilder();
        for (Message msg : messages) {
            String role = getRoleName(msg);
            messageText.append(role).append(": ").append(msg.getText()).append("\n");
        }

        // 将格式化文本填入总结提示模板
        String prompt = String.format(summaryPrompt, messageText.toString());

        // 调用大模型生成总结
        try {
            Prompt summaryPromptObj = new Prompt(List.of(new UserMessage(prompt)));
            var response = model.call(summaryPromptObj);
            return response.getResult().getOutput().getText();
        }
        catch (Exception e) {
            log.error("Failed to create summary: {}", e.getMessage());
            return "Summary generation failed: " + e.getMessage();
        }
    }

    /**
     * 获取消息的角色名称（用于总结文本格式化）。
     *
     * @param message 消息对象
     * @return 角色名称字符串（Human/Assistant/System/Tool/Unknown）
     */
    private String getRoleName(Message message) {
        if (message instanceof UserMessage) {
            return "Human";
        }
        else if (message instanceof AssistantMessage) {
            return "Assistant";
        }
        else if (message instanceof SystemMessage) {
            return "System";
        }
        else if (message instanceof ToolResponseMessage) {
            return "Tool";
        }
        else {
            return "Unknown";
        }
    }

    @Override
    public String getName() {
        return "Summarization";
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of();
    }

    public static class Builder {
        private ChatModel model;
        private Integer maxTokensBeforeSummary;
        private int messagesToKeep = DEFAULT_MESSAGES_TO_KEEP;
        private TokenCounter tokenCounter = TokenCounter.approximateMsgCounter();
        private String summaryPrompt = DEFAULT_SUMMARY_PROMPT;
        private String summaryPrefix = SUMMARY_PREFIX;
        private boolean keepFirstUserMessage = DEFAULT_KEEP_FIRST_USER_MESSAGE;

        public Builder model(ChatModel model) {
            this.model = model;
            return this;
        }

        public Builder maxTokensBeforeSummary(Integer maxTokens) {
            this.maxTokensBeforeSummary = maxTokens;
            return this;
        }

        public Builder messagesToKeep(int count) {
            this.messagesToKeep = count;
            return this;
        }

        public Builder summaryPrompt(String prompt) {
            this.summaryPrompt = prompt;
            return this;
        }

        public Builder summaryPrefix(String prefix) {
            this.summaryPrefix = prefix;
            return this;
        }

        public Builder tokenCounter(TokenCounter counter) {
            this.tokenCounter = counter;
            return this;
        }

        public Builder keepFirstUserMessage(boolean keep) {
            this.keepFirstUserMessage = keep;
            return this;
        }

        public SummarizationHook build() {
            if (model == null) {
                throw new IllegalArgumentException("model must be specified");
            }
            return new SummarizationHook(this);
        }
    }
}