package yumi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("digital_human")
public class DigitalHumanEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("agent_type")
    private String agentType;

    @TableField("parent_code")
    private String parentCode;

    @TableField("avatar")
    private String avatar;

    @TableField("description")
    private String description;

    @TableField("system_prompt")
    private String systemPrompt;

    @TableField("multi_agent_enabled")
    private Integer multiAgentEnabled;

    @TableField("streaming_enabled")
    private Integer streamingEnabled;

    @TableField("skill_ids")
    private String skillIds;

    @TableField(exist = false)
    private java.util.List<Long> skillIdListParsed;

    @TableField("tool_ids")
    private String toolIds;

    @TableField(exist = false)
    private java.util.List<Long> toolIdListParsed;

    @TableField("config")
    private String config;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField("update_user")
    private String updateUser;

    public DigitalHumanEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }

    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public Integer getMultiAgentEnabled() { return multiAgentEnabled; }
    public void setMultiAgentEnabled(Integer multiAgentEnabled) { this.multiAgentEnabled = multiAgentEnabled; }

    public Integer getStreamingEnabled() { return streamingEnabled; }
    public void setStreamingEnabled(Integer streamingEnabled) { this.streamingEnabled = streamingEnabled; }

    public String getSkillIds() { return skillIds; }
    public void setSkillIds(String skillIds) { this.skillIds = skillIds; }

    public java.util.List<Long> getSkillIdListParsed() { return skillIdListParsed; }
    public void setSkillIdListParsed(java.util.List<Long> skillIdListParsed) { this.skillIdListParsed = skillIdListParsed; }

    public String getToolIds() { return toolIds; }
    public void setToolIds(String toolIds) { this.toolIds = toolIds; }

    public java.util.List<Long> getToolIdListParsed() { return toolIdListParsed; }
    public void setToolIdListParsed(java.util.List<Long> toolIdListParsed) { this.toolIdListParsed = toolIdListParsed; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }
}