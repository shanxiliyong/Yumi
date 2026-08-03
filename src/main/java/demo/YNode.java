package demo;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.internal.node.Node;
import lombok.Data;

@Data
public class YNode {



    public interface ActionFactory {

        AsyncNodeActionWithConfig apply(CompileConfig config) throws GraphStateException;

    }

    private final String id;

    private final ActionFactory actionFactory;
}
