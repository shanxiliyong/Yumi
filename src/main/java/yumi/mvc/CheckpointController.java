package yumi.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yumi.entity.CheckpointEntity;
import yumi.entity.DigitalHumanEntity;
import yumi.entity.SessionEntity;
import yumi.service.CheckpointService;
import yumi.service.DigitalHumanService;
import yumi.service.SessionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkpoint")
@CrossOrigin(origins = "*")
public class CheckpointController {

    @Autowired
    private CheckpointService checkpointService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private DigitalHumanService digitalHumanService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam Long sessionId) {
        Map<String, Object> response = new HashMap<>();
        try {
            SessionEntity session = sessionService.getSession(sessionId);
            if (session == null) {
                response.put("success", false);
                response.put("message", "会话不存在");
                return ResponseEntity.badRequest().body(response);
            }

            String threadId = buildThreadId(session);
            List<CheckpointEntity> list = checkpointService.getAllByThreadId(threadId);
            response.put("success", true);
            response.put("data", list);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private String buildThreadId(SessionEntity session) {
        String userId = session.getUserId();
        Long sessionId = session.getId();
        String dhCode = "default";

        if (session.getDigitalHumanId() != null) {
            DigitalHumanEntity dh = digitalHumanService.getById(session.getDigitalHumanId());
            if (dh != null && dh.getCode() != null) {
                dhCode = dh.getCode();
            }
        }

        return userId + "-" + dhCode + "-" + sessionId;
    }
}