package yumi.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import yumi.entity.ToolEntity;

import java.util.*;

@Slf4j
public class RpcToolConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String interfaceName;

    private String methodName;

    private List<ParamConfig> params;

    private List<ParamConfig> responseParams;

    private String group;

    private String version;

    private Integer timeout;

    public RpcToolConfig() {
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

    public List<ParamConfig> getParams() {
        return params;
    }

    public void setParams(List<ParamConfig> params) {
        this.params = params;
    }

    public List<ParamConfig> getResponseParams() {
        return responseParams;
    }

    public void setResponseParams(List<ParamConfig> responseParams) {
        this.responseParams = responseParams;
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

    public static class ParamConfig {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private String example;

        public ParamConfig() {
        }

        public ParamConfig(String name, String type, String description, boolean required) {
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

    public static RpcToolConfig fromToolEntity(ToolEntity tool) {
        if (tool == null || tool.getConfig() == null || tool.getConfig().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> configMap = MAPPER.readValue(tool.getConfig(), new TypeReference<Map<String, Object>>() {});
            RpcToolConfig config = new RpcToolConfig();
            config.setInterfaceName((String) configMap.get("interfaceName"));
            config.setMethodName((String) configMap.get("methodName"));
            config.setGroup((String) configMap.get("group"));
            config.setVersion((String) configMap.get("version"));

            Object timeoutObj = configMap.get("timeout");
            if (timeoutObj != null) {
                config.setTimeout(Integer.parseInt(timeoutObj.toString()));
            } else {
                config.setTimeout(3000);
            }

            Object paramsObj = configMap.get("params");
            if (paramsObj instanceof List) {
                List<ParamConfig> params = new ArrayList<>();
                List<?> paramList = (List<?>) paramsObj;
                for (Object paramObj : paramList) {
                    if (paramObj instanceof Map) {
                        Map<String, Object> paramMap = (Map<String, Object>) paramObj;
                        ParamConfig param = new ParamConfig();
                        param.setName((String) paramMap.get("name"));
                        param.setType((String) paramMap.get("type"));
                        param.setDescription((String) paramMap.get("description"));
                        param.setRequired(Boolean.TRUE.equals(paramMap.get("required")));
                        param.setExample((String) paramMap.get("example"));
                        params.add(param);
                    }
                }
                config.setParams(params);
            }

            Object responseParamsObj = configMap.get("responseParams");
            if (responseParamsObj instanceof List) {
                List<ParamConfig> responseParams = new ArrayList<>();
                List<?> rpList = (List<?>) responseParamsObj;
                for (Object rpObj : rpList) {
                    if (rpObj instanceof Map) {
                        Map<String, Object> rpMap = (Map<String, Object>) rpObj;
                        ParamConfig rp = new ParamConfig();
                        rp.setName((String) rpMap.get("name"));
                        rp.setType((String) rpMap.get("type"));
                        rp.setDescription((String) rpMap.get("description"));
                        rp.setRequired(Boolean.TRUE.equals(rpMap.get("required")));
                        rp.setExample((String) rpMap.get("example"));
                        responseParams.add(rp);
                    }
                }
                config.setResponseParams(responseParams);
            }

            return config;
        } catch (Exception e) {
            log.error("解析 RPC Tool Config 失败: {}", tool.getName(), e);
            return null;
        }
    }

    public String toToolDescription(String toolName, String toolDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append(toolDescription != null ? toolDescription : "RPC 调用工具").append("\n\n");
        sb.append("接口信息:\n");
        sb.append("- 接口名: ").append(interfaceName).append("\n");
        sb.append("- 方法名: ").append(methodName).append("\n");
        if (group != null && !group.isEmpty()) {
            sb.append("- 分组: ").append(group).append("\n");
        }
        if (version != null && !version.isEmpty()) {
            sb.append("- 版本: ").append(version).append("\n");
        }
        if (params != null && !params.isEmpty()) {
            sb.append("\n参数说明:\n");
            for (ParamConfig param : params) {
                sb.append("- ").append(param.getName());
                sb.append(" (").append(param.getType()).append(")");
                if (param.isRequired()) {
                    sb.append(" [必填]");
                } else {
                    sb.append(" [选填]");
                }
                if (param.getDescription() != null && !param.getDescription().isEmpty()) {
                    sb.append(": ").append(param.getDescription());
                }
                if (param.getExample() != null && !param.getExample().isEmpty()) {
                    sb.append(" 示例: ").append(param.getExample());
                }
                sb.append("\n");
            }
        }
        sb.append("\n参数格式: {\"paramName\": \"value\"}");
        if (responseParams != null && !responseParams.isEmpty()) {
            sb.append("\n\n响应字段说明:\n");
            for (ParamConfig rp : responseParams) {
                sb.append("- ").append(rp.getName());
                sb.append(" (").append(rp.getType()).append(")");
                if (rp.getDescription() != null && !rp.getDescription().isEmpty()) {
                    sb.append(": ").append(rp.getDescription());
                }
                if (rp.getExample() != null && !rp.getExample().isEmpty()) {
                    sb.append(" 示例: ").append(rp.getExample());
                }
                sb.append("\n");
            }
            sb.append("\n重要: 仅返回用户查询涉及的字段，无需返回全部字段。");
        }
        return sb.toString();
    }

    public String toJson() {
        try {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("interfaceName", interfaceName);
            configMap.put("methodName", methodName);
            if (group != null) configMap.put("group", group);
            if (version != null) configMap.put("version", version);
            if (timeout != null) configMap.put("timeout", timeout);
            if (params != null) {
                List<Map<String, Object>> paramsList = new ArrayList<>();
                for (ParamConfig param : params) {
                    Map<String, Object> paramMap = new HashMap<>();
                    paramMap.put("name", param.getName());
                    paramMap.put("type", param.getType());
                    paramMap.put("required", param.isRequired());
                    if (param.getDescription() != null) paramMap.put("description", param.getDescription());
                    if (param.getExample() != null) paramMap.put("example", param.getExample());
                    paramsList.add(paramMap);
                }
                configMap.put("params", paramsList);
            }
            if (responseParams != null) {
                List<Map<String, Object>> rpList = new ArrayList<>();
                for (ParamConfig rp : responseParams) {
                    Map<String, Object> rpMap = new HashMap<>();
                    rpMap.put("name", rp.getName());
                    rpMap.put("type", rp.getType());
                    rpMap.put("required", rp.isRequired());
                    if (rp.getDescription() != null) rpMap.put("description", rp.getDescription());
                    if (rp.getExample() != null) rpMap.put("example", rp.getExample());
                    rpList.add(rpMap);
                }
                configMap.put("responseParams", rpList);
            }
            return MAPPER.writeValueAsString(configMap);
        } catch (Exception e) {
            log.error("序列化 RPC Tool Config 失败", e);
            return "{}";
        }
    }

    public static RpcToolConfig createSampleOrderQueryConfig() {
        RpcToolConfig config = new RpcToolConfig();
        config.setInterfaceName("com.example.order.OrderService");
        config.setMethodName("queryOrderDetail");
        config.setGroup("order-group");
        config.setVersion("1.0.0");
        config.setTimeout(5000);

        List<ParamConfig> params = new ArrayList<>();

        ParamConfig orderIdParam = new ParamConfig();
        orderIdParam.setName("orderId");
        orderIdParam.setType("String");
        orderIdParam.setDescription("订单ID");
        orderIdParam.setRequired(true);
        orderIdParam.setExample("10001");
        params.add(orderIdParam);

        ParamConfig orderNoParam = new ParamConfig();
        orderNoParam.setName("orderNo");
        orderNoParam.setType("String");
        orderNoParam.setDescription("订单编号");
        orderNoParam.setRequired(false);
        orderNoParam.setExample("ORD10001");
        params.add(orderNoParam);

        config.setParams(params);

        List<ParamConfig> orderResponseParams = new ArrayList<>();
        orderResponseParams.add(createRp("orderId", "String", "订单ID", "10001"));
        orderResponseParams.add(createRp("orderNo", "String", "订单编号", "ORD10001"));
        orderResponseParams.add(createRp("status", "String", "订单状态", "PAID"));
        orderResponseParams.add(createRp("statusDesc", "String", "状态描述", "已支付"));
        orderResponseParams.add(createRp("amount", "BigDecimal", "订单金额", "299.99"));
        orderResponseParams.add(createRp("createTime", "String", "创建时间/下单时间", "2026-07-28 10:30:00"));
        orderResponseParams.add(createRp("payTime", "String", "支付时间", "2026-07-28 10:32:00"));
        orderResponseParams.add(createRp("shippingAddress", "Object", "收货地址信息", "{...}"));
        orderResponseParams.add(createRp("items", "Array", "商品明细列表", "[...]"));
        orderResponseParams.add(createRp("remark", "String", "备注", "请尽快发货"));
        config.setResponseParams(orderResponseParams);

        return config;
    }

    public static RpcToolConfig createSampleUserQueryConfig() {
        RpcToolConfig config = new RpcToolConfig();
        config.setInterfaceName("com.example.user.UserService");
        config.setMethodName("queryUserInfo");
        config.setGroup("user-group");
        config.setVersion("1.0.0");
        config.setTimeout(3000);

        List<ParamConfig> params = new ArrayList<>();

        ParamConfig userIdParam = new ParamConfig();
        userIdParam.setName("userId");
        userIdParam.setType("String");
        userIdParam.setDescription("用户ID");
        userIdParam.setRequired(true);
        userIdParam.setExample("20001");
        params.add(userIdParam);

        config.setParams(params);

        List<ParamConfig> userResponseParams = new ArrayList<>();
        userResponseParams.add(createRp("userId", "String", "用户ID", "20001"));
        userResponseParams.add(createRp("userName", "String", "用户名", "用户20001"));
        userResponseParams.add(createRp("nickName", "String", "昵称", "昵称20001"));
        userResponseParams.add(createRp("mobile", "String", "手机号", "138****8888"));
        userResponseParams.add(createRp("email", "String", "邮箱", "user@example.com"));
        userResponseParams.add(createRp("status", "String", "账户状态", "ACTIVE"));
        userResponseParams.add(createRp("statusDesc", "String", "状态描述", "正常"));
        userResponseParams.add(createRp("level", "String", "用户等级", "VIP"));
        userResponseParams.add(createRp("registerTime", "String", "注册时间", "2025-01-15 09:00:00"));
        userResponseParams.add(createRp("lastLoginTime", "String", "最近登录时间", "2026-07-28 08:30:00"));
        config.setResponseParams(userResponseParams);

        return config;
    }

    public static RpcToolConfig createSampleProductQueryConfig() {
        RpcToolConfig config = new RpcToolConfig();
        config.setInterfaceName("com.example.product.ProductService");
        config.setMethodName("queryProductDetail");
        config.setGroup("product-group");
        config.setVersion("1.0.0");
        config.setTimeout(3000);

        List<ParamConfig> params = new ArrayList<>();

        ParamConfig productIdParam = new ParamConfig();
        productIdParam.setName("productId");
        productIdParam.setType("String");
        productIdParam.setDescription("商品ID");
        productIdParam.setRequired(true);
        productIdParam.setExample("30001");
        params.add(productIdParam);

        ParamConfig skuIdParam = new ParamConfig();
        skuIdParam.setName("skuId");
        skuIdParam.setType("String");
        skuIdParam.setDescription("SKU ID");
        skuIdParam.setRequired(false);
        skuIdParam.setExample("SKU30001");
        params.add(skuIdParam);

        config.setParams(params);

        List<ParamConfig> productResponseParams = new ArrayList<>();
        productResponseParams.add(createRp("productId", "String", "商品ID", "30001"));
        productResponseParams.add(createRp("skuId", "String", "SKU ID", "SKU30001"));
        productResponseParams.add(createRp("name", "String", "商品名称", "商品30001"));
        productResponseParams.add(createRp("category", "String", "商品分类", "电子产品"));
        productResponseParams.add(createRp("brand", "String", "品牌", "品牌A"));
        productResponseParams.add(createRp("price", "BigDecimal", "售价", "199.99"));
        productResponseParams.add(createRp("originalPrice", "BigDecimal", "原价", "299.99"));
        productResponseParams.add(createRp("stock", "Integer", "库存", "100"));
        productResponseParams.add(createRp("sold", "Integer", "已售数量", "520"));
        productResponseParams.add(createRp("description", "String", "商品描述", "这是一款优质商品"));
        productResponseParams.add(createRp("status", "String", "商品状态", "ON_SALE"));
        productResponseParams.add(createRp("statusDesc", "String", "状态描述", "在售"));
        config.setResponseParams(productResponseParams);

        return config;
    }

    private static ParamConfig createRp(String name, String type, String description, String example) {
        ParamConfig rp = new ParamConfig();
        rp.setName(name);
        rp.setType(type);
        rp.setDescription(description);
        rp.setExample(example);
        return rp;
    }

    @Override
    public String toString() {
        return "RpcToolConfig{" +
                "interfaceName='" + interfaceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", params=" + params +
                ", responseParams=" + responseParams +
                '}';
    }
}