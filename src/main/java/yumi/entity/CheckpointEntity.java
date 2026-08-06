package yumi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("GRAPH_CHECKPOINT")
public class CheckpointEntity {

    @TableId(value = "checkpoint_seq", type = IdType.AUTO)
    private Long checkpointSeq;

    @TableField("checkpoint_id")
    private String checkpointId;

    @TableField("thread_id")
    private String threadId;

    @TableField("base_thread_id")
    private String baseThreadId;

    @TableField("execute_round")
    private Long executeRound;

    @TableField("node_id")
    private String nodeId;

    @TableField("next_node_id")
    private String nextNodeId;

    @TableField("state_data")
    private String stateData;

    @TableField("state_data_json")
    private String stateDataJson;

    @TableField("saved_at")
    private LocalDateTime savedAt;

    @TableField(exist = false)
    private String messageContent;

    @TableField(exist = false)
    private Long duration;

    public CheckpointEntity() {}

    public Long getCheckpointSeq() {
        return checkpointSeq;
    }

    public void setCheckpointSeq(Long checkpointSeq) {
        this.checkpointSeq = checkpointSeq;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getBaseThreadId() {
        return baseThreadId;
    }

    public void setBaseThreadId(String baseThreadId) {
        this.baseThreadId = baseThreadId;
    }

    public Long getExecuteRound() {
        return executeRound;
    }

    public void setExecuteRound(Long executeRound) {
        this.executeRound = executeRound;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNextNodeId() {
        return nextNodeId;
    }

    public void setNextNodeId(String nextNodeId) {
        this.nextNodeId = nextNodeId;
    }

    public String getStateData() {
        return stateData;
    }

    public void setStateData(String stateData) {
        this.stateData = stateData;
    }

    public String getStateDataJson() {
        return stateDataJson;
    }

    public void setStateDataJson(String stateDataJson) {
        this.stateDataJson = stateDataJson;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(LocalDateTime savedAt) {
        this.savedAt = savedAt;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }
}