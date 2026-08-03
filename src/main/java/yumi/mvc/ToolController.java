package yumi.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yumi.entity.ToolEntity;
import yumi.service.ToolService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tool")
@CrossOrigin(origins = "*")
public class ToolController {

    @Autowired
    private ToolService toolService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> data = toolService.list(pageNum, pageSize, keyword);
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> listAll() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<ToolEntity> list = toolService.listAll();
            response.put("success", true);
            response.put("data", list);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            ToolEntity tool = toolService.getById(id);
            response.put("success", tool != null);
            response.put("data", tool);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            ToolEntity tool = new ToolEntity();
            tool.setName(strVal(request.get("name")));
            tool.setType(strVal(request.get("type")));
            tool.setConfig(strVal(request.get("config")));
            tool.setPermission(strVal(request.get("permission"), "public"));
            tool.setDescription(strVal(request.get("description")));
            tool.setUpdateUser(strVal(request.get("updateUser")));
            Map<String, Object> result = toolService.create(tool);
            response.putAll(result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            ToolEntity tool = new ToolEntity();
            tool.setId(id);
            tool.setName(strVal(request.get("name")));
            tool.setType(strVal(request.get("type")));
            tool.setConfig(strVal(request.get("config")));
            tool.setPermission(strVal(request.get("permission")));
            tool.setDescription(strVal(request.get("description")));
            tool.setUpdateUser(strVal(request.get("updateUser")));
            Map<String, Object> result = toolService.update(tool);
            response.putAll(result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> result = toolService.delete(id);
            response.putAll(result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private static String strVal(Object o) {
        return strVal(o, null);
    }

    private static String strVal(Object o, String defaultValue) {
        if (o == null) return defaultValue;
        if (o instanceof String) {
            String s = (String) o;
            return s.isEmpty() ? defaultValue : s;
        }
        return o.toString();
    }
}