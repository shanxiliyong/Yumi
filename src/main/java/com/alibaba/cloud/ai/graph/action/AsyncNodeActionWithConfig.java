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
package com.alibaba.cloud.ai.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import io.opentelemetry.context.Context;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public interface AsyncNodeActionWithConfig
        extends BiFunction<OverAllState, RunnableConfig, CompletableFuture<Map<String, Object>>> {

    /**
     * Applies this action to the given agent state.
     * @param state the agent state
     * @return a CompletableFuture representing the result of the action
     */
    CompletableFuture<Map<String, Object>> apply(OverAllState state, RunnableConfig config);

    /**
     * 将同步节点操作包装为异步节点操作
     * <p>
     * 该方法用于将同步的 NodeActionWithConfig 转换为异步的 AsyncNodeActionWithConfig，
     * 使得同步操作可以在异步图执行框架中使用。
     * <p>
     * 实现原理：
     * 1. 创建一个 CompletableFuture 作为异步结果容器
     * 2. 在当前上下文中执行同步操作
     * 3. 如果执行成功，调用 complete() 设置结果
     * 4. 如果执行异常，调用 completeExceptionally() 设置异常
     * <p>
     * 使用场景：
     * - 当节点操作是同步实现但需要在异步图中使用时
     * - 需要保持上下文（Context）传递的场景
     *
     * @param syncAction 同步节点操作，接收状态和配置，返回结果 Map
     * @return 异步节点操作包装器，返回 CompletableFuture 封装的结果
     */
    static AsyncNodeActionWithConfig node_async(NodeActionWithConfig syncAction) {
        return (state, config) -> {
            // 保存当前上下文，用于异步执行时保持上下文传递
            Context context = Context.current();
            // 创建 CompletableFuture 作为异步结果容器
            CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
            try {
                // 执行同步操作，并将结果设置到 CompletableFuture 中
                result.complete(syncAction.apply(state, config));
            }
            catch (Exception e) {
                // 捕获异常，将异常设置到 CompletableFuture 中
                result.completeExceptionally(e);
            }
            return result;
        };
    }

    /**
     * Adapts a simple AsyncNodeAction to an AsyncNodeActionWithConfig.
     * @param action the simple AsyncNodeAction to be adapted
     * @return an AsyncNodeActionWithConfig that wraps the given AsyncNodeAction
     */
    static AsyncNodeActionWithConfig of(AsyncNodeAction action) {
        if (action instanceof InterruptableAction) {
            return new InterruptableAsyncNodeActionWrapper(action, (InterruptableAction) action);
        }
        return (t, config) -> action.apply(t);
    }

    class InterruptableAsyncNodeActionWrapper implements AsyncNodeActionWithConfig, InterruptableAction {

        private final AsyncNodeAction delegate;
        private final InterruptableAction interruptable;

        public InterruptableAsyncNodeActionWrapper(AsyncNodeAction delegate, InterruptableAction interruptable) {
            this.delegate = delegate;
            this.interruptable = interruptable;
        }

        @Override
        public CompletableFuture<Map<String, Object>> apply(OverAllState state, RunnableConfig config) {
            return delegate.apply(state);
        }

        @Override
        public java.util.Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
            return interruptable.interrupt(nodeId, state, config);
        }

        @Override
        public java.util.Optional<InterruptionMetadata> interruptAfter(String nodeId, OverAllState state,
                                                                       Map<String, Object> actionResult, RunnableConfig config) {
            return interruptable.interruptAfter(nodeId, state, actionResult, config);
        }
    }

}