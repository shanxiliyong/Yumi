package yumi.tool;

import java.util.List;
import java.util.Map;

public class RpcInterfaceSchema {

    private Long id;

    private String name;

    private String description;

    private String interfaceName;

    private String methodName;

    private List<ParamSchema> params;

    private String group;

    private String version;

    private Integer timeout;

    private boolean enabled;

    public RpcInterfaceSchema() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<ParamSchema> getParams() {
        return params;
    }

    public void setParams(List<ParamSchema> params) {
        this.params = params;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static class ParamSchema {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private String example;

        public ParamSchema() {
        }

        public ParamSchema(String name, String type, String description, boolean required) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getExample() {
            return example;
        }

        public void setExample(String example) {
            this.example = example;
        }
    }

    public Map<String, Object> toToolDescription() {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("name", name);
        result.put("description", description);
        result.put("interfaceName", interfaceName);
        result.put("methodName", methodName);
        result.put("params", params);
        result.put("group", group);
        result.put("version", version);
        return result;
    }

    @Override
    public String toString() {
        return "RpcInterfaceSchema{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", interfaceName='" + interfaceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", params=" + params +
                '}';
    }
}