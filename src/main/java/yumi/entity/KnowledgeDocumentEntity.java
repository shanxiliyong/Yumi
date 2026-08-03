package yumi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档实体
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识库ID
     */
    private Long knowledgeBaseId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 文档类型：text/markdown/pdf/word
     */
    private String docType;

    /**
     * 文档来源：upload/url/manual
     */
    private String source;

    /**
     * 文档URL（如果来源是url）
     */
    private String sourceUrl;

    /**
     * 分块数量
     */
    private Integer chunkCount;

    /**
     * 向量ID列表（JSON数组）
     */
    private String vectorIds;

    /**
     * 状态：0-待处理 1-处理中 2-已完成 3-失败
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 创建人
     */
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}