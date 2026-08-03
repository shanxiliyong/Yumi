package yumi.skill;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.AbstractSkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import yumi.entity.SkillEntity;
import yumi.service.SkillService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class DatabaseSkillRegistry extends AbstractSkillRegistry {

    private final SkillService skillService;
    private final List<Long> skillIds;
    private final String registryId;

    private static final SystemPromptTemplate SYSTEM_PROMPT_TEMPLATE = SystemPromptTemplate.builder()
            .template("""
                    ## Skills System
                    
                    You have access to a skills library that provides specialized capabilities and domain knowledge. All skills are stored in a Skill Registry with a database based storage.
                    
                    ### Available Skills
                    {skills_list}
                    
                    ### How to Use Skills (Progressive Disclosure)
                    
                    Skills follow a **progressive disclosure** pattern - you know they exist (name + description above), but you only read the full instructions when needed:
                    
                    1. **Recognize when a skill applies**: Check if the user's task matches any skill's description
                    2. **Read the skill's full instructions**: The skill list above shows the exact skill id to use with `read_skill`
                    3. **Follow the skill's instructions**: SKILL.md contains step-by-step workflows, best practices, and examples
                    4. **Access supporting files**: Skills may include Python scripts, configs, or reference docs - use absolute paths
                    
                    #### How to Read The Full Skill Instruction
                    
                    You are currently using the database based Skill Registry. Please follow the skill loading guidelines below:
                    
                    **Skill Access:**
                    Each skill has a unique name shown in the skill list above. Skill names identify registry entries and are not direct tool names. Use the exact name shown when calling `read_skill` to read the skill content.
                    
                    **Important:**
                    
                    - **For skill names**: Skill names are registry identifiers, not tool names. Do not call a skill name directly as a tool; call `read_skill` with the skill name to load the skill first.
                    - **For skill content**: Always use `read_skill` to read skill instructions. The skill content is stored in the database and loaded on demand.
                    
                    #### When to Use Skills
                    
                    - When the user's request matches a skill's domain
                    - When you need specialized knowledge or structured workflows
                    - When a skill provides proven patterns for complex tasks
                    
                    #### Skills are Self-Documenting
                    
                    Each skill tells you exactly what the skill does and how to use it.
                    
                    Remember: Skills are tools to make you more capable and consistent. When in doubt, check if a skill exists for the task!
                    """)
            .build();

    private DatabaseSkillRegistry(Builder builder) {
        this.skillService = builder.skillService;
        this.skillIds = builder.skillIds;
        this.registryId = builder.registryId;
        loadSkillsToRegistry();
    }

    @Override
    protected void loadSkillsToRegistry() {
        List<SkillEntity> entities;
        if (skillIds != null && !skillIds.isEmpty()) {
            entities = skillService.listByIds(skillIds);
        } else {
            entities = skillService.listEnabled();
        }

        Map<String, SkillMetadata> loaded = entities.stream()
                .filter(e -> e.getStatus() != null && e.getStatus() == 1)
                .map(this::toSkillMetadata)
                .collect(Collectors.toMap(
                        SkillMetadata::getName,
                        m -> m,
                        (a, b) -> a
                ));

        this.skills = loaded;
        log.info("[{}] 从数据库加载了 {} 个 Skill", registryId, loaded.size());
    }

    private SkillMetadata toSkillMetadata(SkillEntity entity) {
        String description = entity.getDescription() != null ? entity.getDescription() : "";
        String skillPath = "/skill/" + entity.getName();
        String content = entity.getContent() != null ? entity.getContent() : "";
        String fullContent = removeFrontmatter(content);
        return SkillMetadata.builder()
                .name(entity.getName())
                .description(description)
                .skillPath(skillPath)
                .source("database")
                .fullContent(fullContent)
                .build();
    }

    private String removeFrontmatter(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (!content.startsWith("---")) {
            return content;
        }
        int endIndex = content.indexOf("---", 3);
        if (endIndex == -1) {
            return content;
        }
        return content.substring(endIndex + 3).trim();
    }

    @Override
    public String readSkillContent(String name) throws IOException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Skill name cannot be null or empty");
        }

        SkillMetadata skill = get(name)
                .orElseThrow(() -> new IllegalStateException("Skill not found: " + name));

        if (skill.getFullContent() != null && !skill.getFullContent().isEmpty()) {
            return skill.getFullContent();
        }

        throw new IOException("Skill content is empty for: " + name);
    }

    @Override
    public String getSkillLoadInstructions() {
        List<SkillMetadata> skills = listAll();
        if (skills.isEmpty()) {
            return "No skills available.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Skill Access:**\n");
        sb.append("Skills are stored in the database. Use the skill name to read skill content via `read_skill`.\n\n");

        sb.append("**Available Skills:**\n");
        for (SkillMetadata skill : skills) {
            sb.append(String.format("- **%s**: %s%n", skill.getName(), skill.getDescription()));
        }

        return sb.toString();
    }

    @Override
    public String getRegistryType() {
        return "Database";
    }

    @Override
    public SystemPromptTemplate getSystemPromptTemplate() {
        return SYSTEM_PROMPT_TEMPLATE;
    }

    public String getRegistryId() {
        return registryId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SkillService skillService;
        private List<Long> skillIds;
        private String registryId = "default";

        public Builder skillService(SkillService skillService) {
            this.skillService = skillService;
            return this;
        }

        public Builder skillIds(List<Long> skillIds) {
            this.skillIds = skillIds;
            return this;
        }

        public Builder registryId(String registryId) {
            this.registryId = registryId;
            return this;
        }

        public DatabaseSkillRegistry build() {
            if (skillService == null) {
                throw new IllegalArgumentException("SkillService is required");
            }
            return new DatabaseSkillRegistry(this);
        }
    }
}