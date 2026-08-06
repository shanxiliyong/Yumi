package yumi.mvc;

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
    private ApplicationContext applicationContext;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private StrategyConfig strategyConfig;

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
                    .type("error")
                    .message(e.getMessage())
                    .build());
        }
        // 审核请求处理
        if (request.getNodeId() != null && request.getApproved() != null) {
            return handleAudit(request);
        }

        // 普通聊天请求
        YumiContext context = new YumiContext();
        context.setRequest(request);

        SessionEntity session = sessionService.getSession(request.getSessionId());
        if (session != null && session.getDigitalHumanId() != null) {
            DigitalHumanEntity dh = digitalHumanService.getById(session.getDigitalHumanId());
            context.setDh(dh);
        }
        AgentResponse agentResponse = yumiAgent.chat(context);

        // 普通消息，更新会话
        if ("normal".equals(agentResponse.getType())) {
            String message = agentResponse.getMessage();
            String lastMsg = message != null && message.length() > 50 ? message.substring(0, 50) + "..." : message;
            sessionService.updateLastMessage(request.getSessionId(), lastMsg);
        }

        return ResponseEntity.ok(agentResponse);
    }

    /**
     * 处理审核请求：确认或取消工具调用
     */
    private ResponseEntity<AgentResponse> handleAudit(ChatRequest request) {
        log.info("audit request: nodeId={}, approved={}", request.getNodeId(), request.getApproved());

        if (request.getApproved()) {
            // 确认执行：恢复图执行
            YumiContext context = new YumiContext();
            context.setRequest(request);

            SessionEntity session = sessionService.getSession(request.getSessionId());
            if (session != null && session.getDigitalHumanId() != null) {
                DigitalHumanEntity dh = digitalHumanService.getById(session.getDigitalHumanId());
                context.setDh(dh);
            }

            // TODO: 调用 agent 恢复执行，传入用户反馈
            // String content = yumiAgent.resumeFromAudit(context, request.getNodeId(), request.getToolFeedbacks());

            // 临时返回
            AgentResponse response = AgentResponse.builder()
                    .type("normal")
                    .message("工具调用已确认执行")
                    .build();

            String lastMsg = response.getMessage().length() > 50 ? response.getMessage().substring(0, 50) + "..." : response.getMessage();
            sessionService.updateLastMessage(request.getSessionId(), lastMsg);

            return ResponseEntity.ok(response);
        } else {
            // 取消执行
            return ResponseEntity.ok(AgentResponse.builder()
                    .type("normal")
                    .message("已取消工具调用")
                    .build());
        }
    }


}