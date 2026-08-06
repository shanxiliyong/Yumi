package yumi.request;

import lombok.Data;

@Data
public class ChatRequest {

    private String userId;

    private Long sessionId;

    private String message;

    /**
     * 响应类型：normal（普通消息）/ audit（审核中断）/ error（错误）
     */
    private String type;

    // 审核相关字段
    private String nodeId;
    private Boolean approved;
}