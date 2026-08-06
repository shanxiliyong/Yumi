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
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        log.info("chat request: {}", request.toString());
        Map<String, Object> response = new HashMap<>();

        try {
            paramCheck(request);
        } catch (YErrorMessageException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }


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
     * 审核接口：确认或取消工具调用
     */
    @PostMapping("/audit")
    public ResponseEntity<Map<String, Object>> audit(@RequestBody Map<String, Object> auditRequest) {
        log.info("audit request: {}", auditRequest);
        Map<String, Object> response = new HashMap<>();

        String userId = (String) auditRequest.get("userId");
        Long sessionId = ((Number) auditRequest.get("sessionId")).longValue();
        String nodeId = (String) auditRequest.get("nodeId");
        Boolean approved = (Boolean) auditRequest.get("approved");

        if (approved) {
            // 确认执行：恢复图执行
            YumiContext context = new YumiContext();
            ChatRequest request = new ChatRequest();
            request.setUserId(userId);
            request.setSessionId(sessionId);
            context.setRequest(request);

            SessionEntity session = sessionService.getSession(sessionId);
            if (session != null && session.getDigitalHumanId() != null) {
                DigitalHumanEntity dh = digitalHumanService.getById(session.getDigitalHumanId());
                context.setDh(dh);
            }

            // TODO: 调用 agent 恢复执行，传入用户反馈
            String content = yumiAgent.resumeFromAudit(context, nodeId, auditRequest);

            String lastMsg = content.length() > 50 ? content.substring(0, 50) + "..." : content;
            sessionService.updateLastMessage(sessionId, lastMsg);

            response.put("success", true);
            response.put("content", content);
        } else {
            // 取消执行
            response.put("success", true);
            response.put("message", "已取消工具调用");
        }

        return ResponseEntity.ok(response);
    }


}