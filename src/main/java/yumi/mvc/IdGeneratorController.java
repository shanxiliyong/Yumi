package yumi.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/id-generator")
@CrossOrigin(origins = "*")
public class IdGeneratorController {

    @Autowired
    private yumi.service.IdGeneratorService idGeneratorService;

    /**
     * 按 code 获取下一个 ID
     * 首次调用（code 不存在）：插入一条 value=1 的记录，返回 1
     * 后续每次调用：value + 1，返回新值
     *
     * @param code 业务编码
     * @return { success: true, code: "xxx", value: N }
     */
    @PostMapping("/next")
    public ResponseEntity<Map<String, Object>> nextId(@RequestParam("code") String code) {
        Map<String, Object> response = new HashMap<>();
        try {
            long value = idGeneratorService.nextId(code);
            response.put("success", true);
            response.put("code", code);
            response.put("value", value);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * GET 方式，方便测试
     */
    @GetMapping("/next")
    public ResponseEntity<Map<String, Object>> nextIdGet(@RequestParam("code") String code) {
        return nextId(code);
    }
}