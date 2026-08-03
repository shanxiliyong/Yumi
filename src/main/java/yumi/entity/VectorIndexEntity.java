package yumi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 向量索引实体
 */
@Data
@TableName("vector_index")
public class VectorIndexEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 分块序号
     */
    private Integer chunkIndex;

    /**
     * 分块内容
     */
    private String chunkContent;

    /**
     * 向量数据(JSON数组)
     */
    private String embedding;

    /**
     * 元数据(JSON)
     */
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}