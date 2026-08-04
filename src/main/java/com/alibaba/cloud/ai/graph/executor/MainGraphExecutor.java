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
package com.alibaba.cloud.ai.graph.executor;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.GraphRunnerContext;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.action.Command;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.utils.TypeRef;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.cloud.ai.graph.GraphRunnerContext.INTERRUPT_AFTER;
import static com.alibaba.cloud.ai.graph.StateGraph.*;

/**
 * Main graph executor that handles the primary execution flow. This class demonstrates
 * inheritance by extending BaseGraphExecutor. It also demonstrates polymorphism through
 * its specific implementation of execute.
 */
public class MainGraphExecutor extends BaseGraphExecutor {

    private final NodeExecutor nodeExecutor;

    public MainGraphExecutor() {
        this.nodeExecutor = new NodeExecutor(this);
    }

    /**
     * 执行图的主要流程
     * 
     * 执行逻辑：
     * 1. 检查是否应该停止或达到最大迭代次数
     * 2. 处理嵌入中断（returnFromEmbed）
     * 3. 处理节点恢复（从断点继续）
     * 4. 处理 START 节点（获取入口点）
     * 5. 处理 END 节点（完成执行）
     * 6. 处理中断逻辑（interruptBeforeEdge）
     * 7. 委托给 NodeExecutor 执行具体节点
     * 
     * @param context 图运行上下文，包含当前状态、节点信息等
     * @param resultValue 原子引用，用于存储最终执行结果
     * @return Flux<GraphResponse<NodeOutput>> 响应式流，包含节点输出
     */
    @Override
    public Flux<GraphResponse<NodeOutput>> execute(GraphRunnerContext context, AtomicReference<Object> resultValue) {
        try {
            // 情况1：检查是否应该停止执行
            if (context.shouldStop() || context.isMaxIterationsReached()) {
                return handleCompletion(context, resultValue);
            }

            // 情况2：处理嵌入的中断（如 Human-in-the-loop 审批后返回）
            final var returnFromEmbed = context.getReturnFromEmbedAndReset();
            if (returnFromEmbed.isPresent()) {
                var interruption = returnFromEmbed.get().value(new TypeRef<InterruptionMetadata>() {
                });
                if (interruption.isPresent()) {
                    return Flux.just(GraphResponse.done(interruption.get()));
                }
                return Flux.just(GraphResponse.done(context.buildNodeOutputAndAddCheckpoint(Map.of())));
            }

            // 情况3：处理节点恢复（用户审批后继续执行）
            if (context.getCurrentNodeId() != null && context.getConfig().isInterrupted(context.getCurrentNodeId())) {
                context.getConfig().withNodeResumed(context.getCurrentNodeId());
                return Flux.just(GraphResponse.done(context.getCurrentStateData()));
            }

            // 情况4：处理 START 节点（图的入口）
            if (context.isStartNode()) {
                return handleStartNode(context);
            }

            // 情况5：处理 END 节点（图的出口）
            if (context.isEndNode()) {
                return handleEndNode(context, resultValue);
            }

            // 情况6：处理从断点恢复（Resume）
            final var resumeFrom = context.getResumeFromAndReset();
            if (resumeFrom.isPresent()) {
                if (context.getCompiledGraph().compileConfig.interruptBeforeEdge()
                        && java.util.Objects.equals(context.getNextNodeId(), INTERRUPT_AFTER)) {
                    var nextNodeCommand = context.nextNodeId(resumeFrom.get(), context.getCurrentStateData());
                    context.setNextNodeId(nextNodeCommand.gotoNode());
                    context.setCurrentNodeId(null);
                }
            }

            // 情况7：检查是否需要在边执行前中断
            if (context.shouldInterrupt()) {
                try {
                    InterruptionMetadata metadata = InterruptionMetadata
                            .builder(context.getCurrentNodeId(), context.cloneState(context.getCurrentStateData()))
                            .build();
                    return Flux.just(GraphResponse.done(metadata));
                }
                catch (Exception e) {
                    return Flux.just(GraphResponse.error(e));
                }
            }

            // 情况8：委托给 NodeExecutor 执行具体节点逻辑
            Flux<GraphResponse<NodeOutput>> execute = nodeExecutor.execute(context, resultValue);
            return execute;
        }
        catch (Exception e) {
            // 异常处理：触发错误监听器并记录日志
            context.doListeners(ERROR, e);
            org.slf4j.LoggerFactory.getLogger(com.alibaba.cloud.ai.graph.GraphRunner.class)
                    .error("Error during graph execution", e);
            return Flux.just(GraphResponse.error(e));
        }
    }

    /**
     * 处理 START 节点执行
     * 
     * 执行流程：
     * 1. 触发 START 事件监听器
     * 2. 获取图的入口点（entryPoint）
     * 3. 设置下一个要执行的节点 ID
     * 4. 创建检查点（Checkpoint）用于状态持久化
     * 5. 构建 START 节点的输出
     * 6. 递归调用 execute() 继续执行下一个节点
     * 
     * @param context 图运行上下文
     * @return Flux<GraphResponse<NodeOutput>> 包含 START 输出和后续执行结果
     */
    private Flux<GraphResponse<NodeOutput>> handleStartNode(GraphRunnerContext context) {
        try {
            // 1. 触发 START 事件监听器
            context.doListeners(START, null);
            
            // 2. 获取图的入口点（通常是第一个要执行的节点）
            Command nextCommand = context.getEntryPoint();
            context.setNextNodeId(nextCommand.gotoNode());

            // 3. 创建检查点，用于状态持久化和断点恢复
            Optional<Checkpoint> cp = context.addCheckpoint(START, context.getNextNodeId());
            
            // 4. 构建 START 节点的输出
            NodeOutput output = context.buildOutput(START, cp);

            // 5. 更新当前节点 ID
            context.setCurrentNodeId(context.getNextNodeId());
            
            // 6. 递归调用 execute() 继续执行下一个节点
            return Flux.just(GraphResponse.of(output))
                    .concatWith(Flux.defer(() -> execute(context, new AtomicReference<>())));
        }
        catch (Exception e) {
            return Flux.just(GraphResponse.error(e));
        }
    }

    /**
     * Handles the end node execution.
     * @param context the graph runner context
     * @param resultValue the atomic reference to store the result value
     * @return Flux of GraphResponse with end node handling result
     */
    private Flux<GraphResponse<NodeOutput>> handleEndNode(GraphRunnerContext context,
                                                          AtomicReference<Object> resultValue) {
        try {
            context.doListeners(END, null);
            NodeOutput output = context.buildNodeOutput(END);
            return Flux.just(GraphResponse.of(output))
                    .concatWith(Flux.defer(() -> handleCompletion(context, resultValue)));
        }
        catch (Exception e) {
            return Flux.just(GraphResponse.error(e));
        }
    }

}