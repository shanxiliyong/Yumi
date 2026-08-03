package yumi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yumi.common.JackJsonUtil;
import yumi.entity.KnowledgeBaseEntity;
import yumi.entity.KnowledgeDocumentEntity;
import yumi.entity.VectorIndexEntity;
import yumi.mapper.KnowledgeBaseMapper;
import yumi.mapper.KnowledgeDocumentMapper;
import yumi.mapper.VectorIndexMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库管理服务
 */
@Slf4j
@Service
public class KnowledgeService extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBaseEntity> {

    private final KnowledgeDocumentMapper documentMapper;
    private final VectorIndexMapper vectorIndexMapper;
    private final VectorStore vectorStore;

    public KnowledgeService(KnowledgeDocumentMapper documentMapper,
                           VectorIndexMapper vectorIndexMapper,
                           VectorStore vectorStore) {
        this.documentMapper = documentMapper;
        this.vectorIndexMapper = vectorIndexMapper;
        this.vectorStore = vectorStore;
    }

    /**
     * 创建知识库
     */
    public KnowledgeBaseEntity createKnowledgeBase(String name, String description, String tenantId) {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setName(name);
        kb.setDescription(description);
        kb.setTenantId(tenantId);
        kb.setStatus(1);
        kb.setUpdateUser("system");
        save(kb);
        log.info("创建知识库成功: id={}, name={}", kb.getId(), name);
        return kb;
    }

    /**
     * 获取用户知识库列表
     */
    public List<KnowledgeBaseEntity> listByTenant(String tenantId) {
        return list(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                .orderByDesc(KnowledgeBaseEntity::getUpdateTime));
    }

    /**
     * 添加文档到知识库
     */
    @Transactional
    public KnowledgeDocumentEntity addDocument(Long knowledgeBaseId, String title, String content,
                                               String docType, String source, String tenantId) {
        KnowledgeDocumentEntity doc = new KnowledgeDocumentEntity();
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setTitle(title);
        doc.setContent(content);
        doc.setDocType(docType != null ? docType : "text");
        doc.setSource(source != null ? source : "manual");
        doc.setStatus(0);
        doc.setTenantId(tenantId);
        doc.setCreatedBy("system");
        documentMapper.insert(doc);

        log.info("添加文档成功: docId={}, title={}", doc.getId(), title);
        return doc;
    }

    /**
     * 获取知识库文档列表
     */
    public List<KnowledgeDocumentEntity> listDocuments(Long knowledgeBaseId) {
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeDocumentEntity::getDeleted, 0)
                .orderByDesc(KnowledgeDocumentEntity::getUpdateTime));
    }

    /**
     * 处理文档：分块、生成向量、存储
     */
    @Transactional
    public void processDocument(Long documentId) {
        KnowledgeDocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.error("文档不存在: {}", documentId);
            return;
        }

        try {
            // 更新状态为处理中
            doc.setStatus(1);
            documentMapper.updateById(doc);

            String content = doc.getContent();
            if (content == null || content.isEmpty()) {
                doc.setStatus(3);
                doc.setErrorMsg("文档内容为空");
                documentMapper.updateById(doc);
                return;
            }

            // 文本分块
            List<String> chunks = splitText(content);
            doc.setChunkCount(chunks.size());

            // 生成向量并存储
            List<Long> vectorIds = new ArrayList<>();
            List<Document> documents = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);

                // 保存到向量索引表
                VectorIndexEntity vectorIndex = new VectorIndexEntity();
                vectorIndex.setDocumentId(documentId);
                vectorIndex.setChunkIndex(i);
                vectorIndex.setChunkContent(chunk);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("knowledgeBaseId", doc.getKnowledgeBaseId());
                metadata.put("chunkIndex", i);
                metadata.put("title", doc.getTitle());
                vectorIndex.setMetadata(JackJsonUtil.toJsonStr(metadata));

                vectorIndexMapper.insert(vectorIndex);
                vectorIds.add(vectorIndex.getId());

                // 准备向量存储
                Document document = new Document(chunk, metadata);
                documents.add(document);
            }

            // 批量添加到向量存储
            vectorStore.add(documents);

            // 更新文档状态
            doc.setVectorIds(JackJsonUtil.toJsonStr(vectorIds));
            doc.setStatus(2);
            documentMapper.updateById(doc);

            log.info("文档处理完成: docId={}, chunks={}", documentId, chunks.size());
        } catch (Exception e) {
            log.error("文档处理失败: docId={}", documentId, e);
            doc.setStatus(3);
            doc.setErrorMsg(e.getMessage());
            documentMapper.updateById(doc);
        }
    }

    /**
     * 相似度搜索
     */
    public List<Map<String, Object>> similaritySearch(Long knowledgeBaseId, String query, int topK) {
        Map<String, Object> filter = Map.of("knowledgeBaseId", knowledgeBaseId);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("knowledgeBaseId == '" + knowledgeBaseId + "'")
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        return results.stream()
                .map(doc -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("content", doc.getText());
                    map.put("metadata", doc.getMetadata());
                    map.put("score", doc.getScore());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * 删除知识库
     */
    @Transactional
    public void deleteKnowledgeBase(Long id) {
        List<KnowledgeDocumentEntity> docs = documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, id));

        for (KnowledgeDocumentEntity doc : docs) {
            deleteDocument(doc.getId());
        }

        removeById(id);
        log.info("删除知识库成功: id={}", id);
    }

    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        // 删除向量索引
        vectorIndexMapper.delete(new LambdaQueryWrapper<VectorIndexEntity>()
                .eq(VectorIndexEntity::getDocumentId, documentId));

        // 删除文档记录
        documentMapper.deleteById(documentId);
        log.info("删除文档成功: docId={}", documentId);
    }

    /**
     * 文本分块
     */
    private List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        int chunkSize = 500;
        int overlap = 50;

        if (text.length() <= chunkSize) {
            return List.of(text);
        }

        for (int i = 0; i < text.length(); i += chunkSize - overlap) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
            if (end == text.length()) break;
        }

        return chunks.isEmpty() ? List.of(text) : chunks;
    }
}