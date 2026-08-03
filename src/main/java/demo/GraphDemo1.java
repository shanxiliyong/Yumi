package demo;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.state.strategy.MergeStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

public class GraphDemo1 {


    public static void main(String[] args) throws Exception {
        // 1. 定义状态键的合并策略
        // messages 使用 AppendStrategy，counter 使用 ReplaceStrategy 作为对比
        StateGraph graph = new StateGraph("append_debug_demo", () -> Map.of(
                "messages", new MergeStrategy(),
                "counter", new ReplaceStrategy()
        ));

        // 2. 添加节点：模拟两个节点分别向 messages 追加数据
        graph.addNode("step1", node_async(state -> {
            System.out.println("[Step1] 正在追加第一条消息...");
            // 返回要更新的状态，框架会根据 AppendStrategy 自动追加到列表中
            return Map.of(
                    "messages", List.of("Hello, this is message 1"),
                    "counter", 1
            );
        }));

        graph.addNode("step2", node_async(state -> {
            System.out.println("[Step2] 正在追加第二条消息...");
            return Map.of(
                    "messages", List.of("Hi, this is message 2"),
                    "counter", 2
            );
        }));

        // 3. 定义流转路径
        graph.addEdge(START, "step1");
        graph.addEdge("step1", "step2");
        graph.addEdge("step2", END);
        graph.addConditionalEdges("feedback_classifier",
                edge_async(new IntentDispatcher()),
                Map.of("positive", "recorder", "negative", "specific_question_classifier"));

        // 4. 编译并执行
        CompiledGraph compiled = graph.compile();
        OverAllState result = compiled.invoke(Map.of()).get();

        // 5. 打印最终结果（在这里打断点查看 result.data()）
        System.out.println("========== Debug 结果 ==========");
        // 使用 value() 方法获取状态，并提供默认值防止空指针
        System.out.println("Messages: " + result.value("messages", new java.util.ArrayList<>()));
        System.out.println("Counter: " + result.value("counter", 0));
    }


    /**
     * 自定义路由分发器：实现 AsyncEdgeAction
     * 它的作用是从全局状态中提取分类结果，并返回对应的路由键
     */
    static class IntentDispatcher implements EdgeAction {
        @Override
        public String apply(OverAllState state) {
            String intent = (String) state.value("intent").orElse("unknown");
            System.out.println("[Dispatcher] 识别到意图: " + intent + "，准备路由...");
            return intent; // 返回的字符串必须与 addConditionalEdges 中的 Map Key 对应
        }
    }
}
