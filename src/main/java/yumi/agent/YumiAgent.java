package yumi.agent;

import reactor.core.publisher.Flux;
import yumi.common.YumiContext;

public interface YumiAgent {

    String chat(YumiContext context);

    Flux<String> chatStream(YumiContext context);
}