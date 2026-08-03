package yumi.mvc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yumi.entity.KnowledgeBaseEntity;
import yumi.entity.KnowledgeDocumentEntity;
import yumi.service.KnowledgeService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * 创建知识库
     */
    @PostMapping("/base")
    public ResponseEntity<Map<String, Object>> createKnowledgeBase(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tenantId", required = false, defaultValue = "default") String tenantId) {
        Map<String, Object> response = new HashMap<>();
        try {
            KnowledgeBaseEntity kb = knowledgeService.createKnowledgeBase(name, description, tenantId);
            response.put("success", true);
            response.put("data", kb);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建知识库失败", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取知识库列表
     */
    @GetMapping("/base/list")
    public ResponseEntity<Map<String, Object>> listKnowledgeBases(
            @RequestParam(value = "tenantId", required = false, defaultValue = "default") String tenantId) {
        Map<String, Object> response = new HashMap<>();
        List<KnowledgeBaseEntity> list = knowledgeService.listByTenant(tenantId);
        response.put("success", true);
        response.put("data", list);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/base/{id}")
    public ResponseEntity<Map<String, Object>> deleteKnowledgeBase(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            knowledgeService.deleteKnowledgeBase(id);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除知识库失败", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 添加文档
     */
    @PostMapping("/document")
    public ResponseEntity<Map<String, Object>> addDocument(
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "docType", required = false, defaultValue = "text") String docType,
            @RequestParam(value = "source", required = false, defaultValue = "manual") String source,
            @RequestParam(value = "tenantId", required = false, defaultValue = "default") String tenantId) {
        Map<String, Object> response = new HashMap<>();
        try {
            KnowledgeDocumentEntity doc = knowledgeService.addDocument(knowledgeBaseId, title, content, docType, source, tenantId);
            response.put("success", true);
            response.put("data", doc);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("添加文档失败", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取文档列表
     */
    @GetMapping("/document/list")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId) {
        Map<String, Object> response = new HashMap<>();
        List<KnowledgeDocumentEntity> list = knowledgeService.listDocuments(knowledgeBaseId);
        response.put("success", true);
        response.put("data", list);
        return ResponseEntity.ok(response);
    }

    /**
     * 处理文档（分块、向量化）
     */
    @PostMapping("/document/process")
    public ResponseEntity<Map<String, Object>> processDocument(
            @RequestParam("documentId") Long documentId) {
        Map<String, Object> response = new HashMap<>();
        try {
            knowledgeService.processDocument(documentId);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("处理文档失败", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/document/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            knowledgeService.deleteDocument(id);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除文档失败", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 相似度搜索
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> similaritySearch(
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId,
            @RequestParam("query") String query,
            @RequestParam(value = "topK", required = false, defaultValue = "5") int topK) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> results = knowledgeService.similaritySearch(knowledgeBaseId, query, topK);
            response.put("success", true);
            response.put("data", results);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("相似度搜索失败", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}