package yumi.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yumi.entity.DigitalHumanEntity;
import yumi.service.DigitalHumanService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/digital-human")
@CrossOrigin(origins = "*")
public class DigitalHumanController {

    @Autowired
    private DigitalHumanService digitalHumanService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> data = digitalHumanService.list(pageNum, pageSize, keyword);
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/children")
    public ResponseEntity<Map<String, Object>> listChildren(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String parentCode,
            @RequestParam(required = false) String keyword) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> data = digitalHumanService.listChildren(pageNum, pageSize, parentCode, keyword);
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
            List<DigitalHumanEntity> list = digitalHumanService.listAll();
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
            DigitalHumanEntity entity = digitalHumanService.getById(id);
            if (entity != null) {
                if (entity.getSkillIds() != null && !entity.getSkillIds().isEmpty()) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        List<Long> skillIdList = mapper.readValue(entity.getSkillIds(), new TypeReference<List<Long>>() {});
                        entity.setSkillIdListParsed(skillIdList);
                    } catch (Exception e) {
                        // ignore parse error
                    }
                }
                if (entity.getToolIds() != null && !entity.getToolIds().isEmpty()) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        List<Long> toolIdList = mapper.readValue(entity.getToolIds(), new TypeReference<List<Long>>() {});
                        entity.setToolIdListParsed(toolIdList);
                    } catch (Exception e) {
                        // ignore parse error
                    }
                }
            }
            response.put("success", entity != null);
            response.put("data", entity);
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
            DigitalHumanEntity entity = new DigitalHumanEntity();
            entity.setCode(strVal(request.get("code")));
            entity.setName(strVal(request.get("name")));
            entity.setAgentType(strVal(request.get("agentType"), "parent"));
            entity.setParentCode(strVal(request.get("parentCode")));
            entity.setAvatar(strVal(request.get("avatar")));
            entity.setDescription(strVal(request.get("description")));
            entity.setSystemPrompt(strVal(request.get("systemPrompt")));
            entity.setMultiAgentEnabled(intVal(request.get("multiAgentEnabled"), 0));
            entity.setStreamingEnabled(intVal(request.get("streamingEnabled"), 0));
            entity.setSkillIds(parseSkillIds(request.get("skillIds")));
            entity.setToolIds(parseSkillIds(request.get("toolIds")));
            entity.setConfig(parseConfig(request.get("config")));
            entity.setUpdateUser(strVal(request.get("updateUser")));
            Map<String, Object> result = digitalHumanService.create(entity);
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
            DigitalHumanEntity entity = new DigitalHumanEntity();
            entity.setId(id);
            entity.setCode(strVal(request.get("code")));
            entity.setName(strVal(request.get("name")));
            entity.setAgentType(strVal(request.get("agentType")));
            entity.setParentCode(strVal(request.get("parentCode")));
            entity.setAvatar(strVal(request.get("avatar")));
            entity.setDescription(strVal(request.get("description")));
            entity.setSystemPrompt(strVal(request.get("systemPrompt")));
            entity.setMultiAgentEnabled(intVal(request.get("multiAgentEnabled"), 0));
            entity.setStreamingEnabled(intVal(request.get("streamingEnabled"), 0));
            entity.setSkillIds(parseSkillIds(request.get("skillIds")));
            entity.setToolIds(parseSkillIds(request.get("toolIds")));
            entity.setConfig(parseConfig(request.get("config")));
            entity.setUpdateUser(strVal(request.get("updateUser")));
            Map<String, Object> result = digitalHumanService.update(entity);
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
            Map<String, Object> result = digitalHumanService.delete(id);
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

    private static Integer intVal(Object o, Integer defaultValue) {
        if (o == null) return defaultValue;
        if (o instanceof Integer) {
            return (Integer) o;
        }
        if (o instanceof String) {
            String s = (String) o;
            return s.isEmpty() ? defaultValue : Integer.parseInt(s);
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String parseSkillIds(Object skillIds) {
        if (skillIds == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            if (skillIds instanceof List) {
                return mapper.writeValueAsString(skillIds);
            }
            if (skillIds instanceof String) {
                String s = (String) skillIds;
                return s.isEmpty() ? null : s;
            }
            return mapper.writeValueAsString(skillIds);
        } catch (Exception e) {
            return null;
        }
    }

    private static String parseConfig(Object config) {
        if (config == null) return null;
        if (config instanceof String) {
            String s = (String) config;
            return s.isEmpty() ? null : s;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(config);
        } catch (Exception e) {
            return null;
        }
    }
}