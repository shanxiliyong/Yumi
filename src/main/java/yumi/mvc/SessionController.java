package yumi.mvc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yumi.common.InterruptionCache;
import yumi.common.JackJsonUtil;
import yumi.common.YumiContext;
import yumi.entity.DigitalHumanEntity;
import yumi.entity.SessionEntity;
import yumi.request.ChatRequest;
import yumi.service.CheckpointService;
import yumi.service.DigitalHumanService;
import yumi.service.SessionService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private CheckpointService checkpointService;

    @Autowired
    private DigitalHumanService digitalHumanService;

    @Autowired
    private InterruptionCache interruptionCache;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSessions(@RequestParam("userId") String userId) {
        Map<String, Object> response = new HashMap<>();
        List<SessionEntity> sessions = sessionService.getSessionsByUserId(userId);

        List<Map<String, Object>> userSessions = new ArrayList<>();
        for (SessionEntity session : sessions) {
            DigitalHumanEntity digitalHuman = null;
            String requestType = "send"; // 默认流式

            if (session.getDigitalHumanId() != null) {
                digitalHuman = digitalHumanService.getById(session.getDigitalHumanId());
                if (digitalHuman != null) {
                    requestType = digitalHuman.getStreamingEnabled() == 1 ? "stream" : "send";
                }
            }

            userSessions.add(Map.of(
                    "sessionId", session.getId(),
                    "name", session.getName(),
                    "requestType", requestType
            ));
        }

        response.put("success", true);
        response.put("data", userSessions);
        log.info("获取会话列表 - userId: {}, sessions: {}", userId, JackJsonUtil.toJsonStr(userSessions));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestParam("userId") String userId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "digitalHumanId", required = false) Long digitalHumanId) {
        log.info("createSession request: {}, {}, {}", userId, name, digitalHumanId);
        Map<String, Object> response = new HashMap<>();
        String sessionName = name != null && !name.trim().isEmpty() ? name : "新对话";
        System.out.println("创建会话 - userId: " + userId + ", name: " + sessionName + ", digitalHumanId: " + digitalHumanId);
        Long sessionId = sessionService.createSession(userId, sessionName, digitalHumanId);
        String requestType = "send"; // 默认流式

        DigitalHumanEntity digitalHuman = digitalHumanService.getById(digitalHumanId);
        if (digitalHuman != null) {
            requestType = digitalHuman.getStreamingEnabled() == 1 ? "stream" : "send";
        }
        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("name", sessionName);
        response.put("requestType", requestType);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> updateSession(
            @PathVariable("sessionId") Long sessionId,
            @RequestParam("name") String name) {

        Map<String, Object> response = new HashMap<>();

        boolean success = sessionService.updateSession(sessionId, name);

        if (success) {
            response.put("success", true);
            response.put("message", "更新成功");
        } else {
            response.put("success", false);
            response.put("message", "会话不存在");
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable("sessionId") Long sessionId) {
        Map<String, Object> response = new HashMap<>();

        boolean success = sessionService.deleteSession(sessionId);

        if (success) {
            response.put("success", true);
            response.put("message", "删除成功");
        } else {
            response.put("success", false);
            response.put("message", "会话不存在");
        }
        return ResponseEntity.ok(response);
    }


    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> getSessionMessages(@RequestBody ChatRequest request) {
        log.info("getSessionMessages request: {}", request.toString());
        Map<String, Object> response = new HashMap<>();
        YumiContext context = new YumiContext();
        context.setRequest(request);
        SessionEntity session = sessionService.getSession(request.getSessionId());
        if (session != null && session.getDigitalHumanId() != null) {
            DigitalHumanEntity dh = digitalHumanService.getById(session.getDigitalHumanId());
            context.setDh(dh);
        }
        String threadId = context.getSessionKey();
        List<Map<String, Object>> messages = checkpointService.getMessagesFromCheckpoint(threadId);

        // 查询该会话的最新待审核中断
        com.alibaba.cloud.ai.graph.action.InterruptionMetadata latestInterruption = interruptionCache.getLatest(threadId);
        List<Map<String, Object>> auditInfo = new ArrayList<>();
        if (latestInterruption != null) {
            Map<String, Object> auditItem = new HashMap<>();
            auditItem.put("nodeId", latestInterruption.node());
            auditItem.put("toolFeedbacks", latestInterruption.toolFeedbacks());
            auditInfo.add(auditItem);
        }

        response.put("success", true);
        response.put("data", messages);
        response.put("threadId", threadId);
        response.put("auditInfo", auditInfo);
        return ResponseEntity.ok(response);
    }


}