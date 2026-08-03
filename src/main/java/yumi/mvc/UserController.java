package yumi.mvc;

 import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yumi.entity.SessionEntity;
import yumi.service.SessionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private SessionService sessionService;

    private Map<String, String> sessionStore = new HashMap<>();

    /**
     * 用户登录 - 只需输入用户名即可登录
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        
        if (username == null || username.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "请输入用户名");
            return ResponseEntity.badRequest().body(error);
        }

        String trimmedUsername = username.trim();

        // 生成会话ID
        String sessionId = UUID.randomUUID().toString();
        sessionStore.put(sessionId, trimmedUsername);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "登录成功");
        response.put("sessionId", sessionId);
        response.put("username", trimmedUsername);

        return ResponseEntity.ok(response);
    }

    /**
     * 检查登录状态
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkLogin(@RequestHeader("X-Session-Id") String sessionId) {
        Map<String, Object> response = new HashMap<>();
        
        if (sessionId != null && sessionStore.containsKey(sessionId)) {
            response.put("success", true);
            response.put("loggedIn", true);
            response.put("username", sessionStore.get(sessionId));
        } else {
            response.put("success", true);
            response.put("loggedIn", false);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 用户退出登录
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader("X-Session-Id") String sessionId) {
        Map<String, Object> response = new HashMap<>();
        
        if (sessionId != null && sessionStore.containsKey(sessionId)) {
            sessionStore.remove(sessionId);
            response.put("success", true);
            response.put("message", "退出成功");
        } else {
            response.put("success", false);
            response.put("message", "未登录");
        }

        return ResponseEntity.ok(response);
    }
}