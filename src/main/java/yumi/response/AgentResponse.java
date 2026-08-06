package yumi.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行响应
 */
@Data
@Builder
public class AgentResponse implements Serializable {

    /**
     * 响应类型：normal（普通消息）/ audit（审核中断）/ error（错误）
     */
    private String type;

    /**
     * 消息内容（普通消息时返回）
     */
    private String message;

    /**
     * 确认信息（审核中断时返回，工具反馈列表）
     */
    private List<?> confirmInfo;

    /**
     * 额外信息（审核中断时返回，包含 nodeId 等）
     */
    private Map<String, Object> extraInfo;
}