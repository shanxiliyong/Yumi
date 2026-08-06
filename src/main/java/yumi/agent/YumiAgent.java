package yumi.agent;

import reactor.core.publisher.Flux;
import yumi.common.YumiContext;
import yumi.request.ChatRequest;
import yumi.response.AgentResponse;

public interface YumiAgent {

    AgentResponse chat(ChatRequest request);

    Flux<String> chatStream(YumiContext context);
}