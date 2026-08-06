package yumi.mvc;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import yumi.agent.BaseYumiAgent;
import yumi.agent.YumiAgent;
import yumi.common.ConstantUtil;
import yumi.common.YErrorMessageException;
import yumi.common.YumiContext;
import yumi.config.StrategyConfig;
import yumi.entity.DigitalHumanEntity;
import yumi.entity.SessionEntity;
import yumi.request.ChatRequest;
import yumi.response.AgentResponse;
import yumi.service.DigitalHumanService;
import yumi.service.SessionService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {



    @Autowired
    private SessionService sessionService;



    @Autowired
    private DigitalHumanService digitalHumanService;

    @Autowired
    BaseYumiAgent yumiAgent;



    private Map<String, YumiAgent> agentMap = new ConcurrentHashMap<>();

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + "; charset=utf-8")
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("chatStream request: {}", request.toString());
        String message = request.getMessage();
        try {
            paramCheck(request);
        } catch (YErrorMessageException e) {
            return Flux.just(e.getMessage());
        }

        YumiContext context = new YumiContext();
        context.setRequest(request);
        SessionEntity session = sessionService.getSession(request.getSessionId());
        if (session != null && session.getDigitalHumanId() != null) {
            DigitalHumanEntity dh = digitalHumanService.getById(session.getDigitalHumanId());
            context.setDh(dh);
        }
        return yumiAgent.chatStream(context)
                .doOnComplete(() -> {
                    log.info("doOnComplete sessionKey: {} message: {}", context.getSessionKey(), message);
                    String lastMsg = message.length() > 50 ? message.substring(0, 50) + "..." : message;
                    sessionService.updateLastMessage(request.getSessionId(), lastMsg);
                });
    }

    @Nullable
    private static void paramCheck(ChatRequest request) {
        if (StringUtils.isEmpty(request.getUserId())) {
            throw new YErrorMessageException("[错误] 用户ID不能为空");
        }

        if (request.getSessionId() == null) {
            throw new YErrorMessageException("[错误] 会话ID不能为空");
        }

        if (StringUtils.isEmpty(request.getMessage())) {
            throw new YErrorMessageException("[错误] 消息不能为空");
        }
    }

    @PostMapping("/send")
    public ResponseEntity<AgentResponse> chat(@RequestBody ChatRequest request) {
        log.info("chat request: {}", request.toString());

        try {
            paramCheck(request);
        } catch (YErrorMessageException e) {
            return ResponseEntity.ok(AgentResponse.builder()
                    .type(ConstantUtil.TYPE_ERROR)
                    .message(e.getMessage())
                    .build());
        }


        AgentResponse agentResponse = yumiAgent.chat(request);

        return ResponseEntity.ok(agentResponse);
    }


}