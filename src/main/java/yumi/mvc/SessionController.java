package yumi.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private CheckpointService checkpointService;

    @Autowired
    private DigitalHumanService digitalHumanService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSessions(@RequestParam("userId") String userId) {
        Map<String, Object> response = new HashMap<>();
        List<SessionEntity> sessions = sessionService.getSessionsByUserId(userId);

        List<Map<String, Object>> userSessions = new ArrayList<>();
        for (SessionEntity session : sessions) {
            userSessions.add(Map.of(
                    "sessionId", session.getId(),
                    "name", session.getName(),
                    "createdAt", session.getCreateTime() == null ? "" : session.getCreateTime().toString(),
                    "lastMessage", session.getLastMessage() == null ? "暂无消息" : session.getLastMessage()
            ));
        }

        response.put("success", true);
        response.put("data", userSessions);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestParam("userId") String userId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "digitalHumanId", required = false) Long digitalHumanId) {

        Map<String, Object> response = new HashMap<>();
        String sessionName = name != null && !name.trim().isEmpty() ? name : "新对话";
        Long sessionId = sessionService.createSession(userId, sessionName, digitalHumanId);

        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("name", sessionName);
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
    public ResponseEntity<Map<String, Object>> getSessionMessages(@RequestBody ChatRequest   request) {
        Map<String, Object> response = new HashMap<>();

        YumiContext context = new YumiContext();
        context.setRequest(request);

        if (request.getDigitalHumanId() != null) {
            DigitalHumanEntity dh = digitalHumanService.getById(request.getDigitalHumanId());
            context.setDh(dh);
        }
        String threadId = context.getSessionKey();
        List<Map<String, Object>> messages = checkpointService.getMessagesFromCheckpoint(threadId);

        response.put("success", true);
        response.put("data", messages);
        response.put("threadId", threadId);
        return ResponseEntity.ok(response);
    }


}