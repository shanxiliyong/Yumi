package yumi.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yumi.entity.SkillEntity;
import yumi.service.SkillService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skill")
@CrossOrigin(origins = "*")
public class SkillController {

    @Autowired
    private SkillService skillService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> data = skillService.list(pageNum, pageSize, keyword);
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
            List<SkillEntity> list = skillService.listAll();
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
            SkillEntity skill = skillService.getById(id);
            response.put("success", skill != null);
            response.put("data", skill);
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
            SkillEntity skill = new SkillEntity();
            skill.setName(strVal(request.get("name")));
            skill.setCategory(strVal(request.get("category")));
            skill.setVersion(strVal(request.get("version"), "v1.0.0"));
            skill.setContent(strVal(request.get("content")));
            skill.setDescription(strVal(request.get("description")));
            skill.setStatus(request.get("status") != null ? Integer.parseInt(request.get("status").toString()) : 1);
            skill.setUpdateUser(strVal(request.get("updateUser")));
            Map<String, Object> result = skillService.create(skill);
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
            SkillEntity skill = new SkillEntity();
            skill.setId(id);
            skill.setName(strVal(request.get("name")));
            skill.setCategory(strVal(request.get("category")));
            skill.setVersion(strVal(request.get("version")));
            skill.setContent(strVal(request.get("content")));
            skill.setDescription(strVal(request.get("description")));
            if (request.get("status") != null) {
                skill.setStatus(Integer.parseInt(request.get("status").toString()));
            }
            skill.setUpdateUser(strVal(request.get("updateUser")));
            Map<String, Object> result = skillService.update(skill);
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
            Map<String, Object> result = skillService.delete(id);
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