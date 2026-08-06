package yumi.agent;

import reactor.core.publisher.Flux;
import yumi.common.YumiContext;
import yumi.response.AgentResponse;

public interface YumiAgent {

    AgentResponse chat(YumiContext context);

    Flux<String> chatStream(YumiContext context);
}