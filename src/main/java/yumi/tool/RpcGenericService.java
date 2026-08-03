package yumi.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class RpcGenericService {

    public Map<String, Object> invoke(RpcCallRequest request) {
        log.info("RPC 调用请求: interfaceName={}, methodName={}, params={}",
                request.getInterfaceName(), request.getMethodName(), request.getParams());

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("data", null);
        result.put("message", "");

        try {
            if (request.getInterfaceName() == null || request.getInterfaceName().isEmpty()) {
                result.put("message", "接口名称(interfaceName)不能为空");
                return result;
            }

            if (request.getMethodName() == null || request.getMethodName().isEmpty()) {
                result.put("message", "方法名称(methodName)不能为空");
                return result;
            }

            Map<String, Object> mockData = generateMockData(request);

            result.put("success", true);
            result.put("data", mockData);
            result.put("message", "Mock 调用成功");
            result.put("mock", true);

        } catch (Exception e) {
            log.error("RPC 调用异常", e);
            result.put("message", "调用异常: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> generateMockData(RpcCallRequest request) {
        String interfaceName = request.getInterfaceName();
        String methodName = request.getMethodName();
        Map<String, Object> params = request.getParams();

        log.info("生成 Mock 数据: {}.{}({})", interfaceName, methodName, params);

        Map<String, Object> mockData = new HashMap<>();

        // 订单相关接口 Mock：生成订单详情、订单状态、订单列表等数据
        if (isOrderInterface(interfaceName)) {
            mockData = generateOrderMock(methodName, params);

        // 用户相关接口 Mock：生成用户信息、用户状态、用户资料等数据
        } else if (isUserInterface(interfaceName)) {
            mockData = generateUserMock(methodName, params);

        // 商品相关接口 Mock：生成商品详情、商品列表、库存信息等数据
        } else if (isProductInterface(interfaceName)) {
            mockData = generateProductMock(methodName, params);

        // 上下文诊断接口 Mock：分析参数缺失原因(diagnoseMissingParam)、参数值不合法原因(diagnoseInvalidParam)等
        } else if (isContextInterface(interfaceName)) {
            mockData = generateContextMock(methodName, params);

        // 组件诊断接口 Mock：分析前端组件不展示原因(diagnoseComponentNotDisplay)、组件权限校验等
        } else if (isComponentInterface(interfaceName)) {
            mockData = generateComponentMock(methodName, params);


        // 通用接口 Mock：返回接口名称、方法名、参数等基础信息
        } else {
            mockData = generateGenericMock(request);
        }

        return mockData;
    }

    private boolean isOrderInterface(String interfaceName) {
        return interfaceName.toLowerCase().contains("order");
    }

    private boolean isUserInterface(String interfaceName) {
        return interfaceName.toLowerCase().contains("user");
    }

    private boolean isProductInterface(String interfaceName) {
        return interfaceName.toLowerCase().contains("product") || interfaceName.toLowerCase().contains("goods") || interfaceName.toLowerCase().contains("item");
    }

    private boolean isContextInterface(String interfaceName) {
        return interfaceName.toLowerCase().contains("context");
    }

    private boolean isComponentInterface(String interfaceName) {
        return interfaceName.toLowerCase().contains("component");
    }

    private Map<String, Object> generateOrderMock(String methodName, Map<String, Object> params) {
        Map<String, Object> data = new HashMap<>();
        String orderId = params != null ? String.valueOf(params.getOrDefault("orderId", params.get("orderNo"))) : "10001";

        data.put("orderId", orderId);
        data.put("orderNo", "ORD" + orderId);
        data.put("status", "PAID");
        data.put("statusDesc", "已支付_"+orderId);
        data.put("amount", 299.99);
        data.put("createTime", "2026-07-28 10:30:00");
        data.put("payTime", "2026-07-28 10:32:00");
        data.put("shippingAddress", Map.of(
                "receiverName", "张三",
                "receiverPhone", "138****8888",
                "address", "上海市浦东新区陆家嘴环路1000号"
        ));
        data.put("items", Arrays.asList(
                Map.of("skuId", "SKU001", "name", "商品A", "price", 99.99, "quantity", 2),
                Map.of("skuId", "SKU002", "name", "商品B", "price", 100.01, "quantity", 1)
        ));
        data.put("remark", "请尽快发货_");

        log.info("生成订单 Mock 数据: orderId={}", orderId);
        return data;
    }

    private Map<String, Object> generateUserMock(String methodName, Map<String, Object> params) {
        Map<String, Object> data = new HashMap<>();
        String userId = params != null ? String.valueOf(params.getOrDefault("userId", "20001")) : "20001";

        data.put("userId", userId);
        data.put("userName", "用户" + userId);
        data.put("nickName", "昵称" + userId);
        data.put("mobile", "138****" + userId.substring(Math.max(0, userId.length() - 4)));
        data.put("email", "user" + userId + "@example.com");
        data.put("status", "ACTIVE");
        data.put("statusDesc", "正常");
        data.put("level", "VIP");
        data.put("registerTime", "2025-01-15 09:00:00");
        data.put("lastLoginTime", "2026-07-28 08:30:00");

        log.info("生成用户 Mock 数据: userId={}", userId);
        return data;
    }

    private Map<String, Object> generateProductMock(String methodName, Map<String, Object> params) {
        Map<String, Object> data = new HashMap<>();
        String productId = params != null ? String.valueOf(params.getOrDefault("productId", "30001")) : "30001";

        data.put("productId", productId);
        data.put("skuId", "SKU" + productId);
        data.put("name", "商品" + productId);
        data.put("category", "电子产品");
        data.put("brand", "品牌A");
        data.put("price", 199.99);
        data.put("originalPrice", 299.99);
        data.put("stock", 100);
        data.put("sold", 520);
        data.put("description", "这是一款优质商品，值得购买");
        data.put("images", Arrays.asList(
                "https://example.com/img1.jpg",
                "https://example.com/img2.jpg"
        ));
        data.put("tags", Arrays.asList("热销", "新品"));
        data.put("status", "ON_SALE");
        data.put("statusDesc", "在售");

        log.info("生成商品 Mock 数据: productId={}", productId);
        return data;
    }

    private Map<String, Object> generateGenericMock(RpcCallRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("interfaceName", request.getInterfaceName());
        data.put("methodName", request.getMethodName());
        data.put("params", request.getParams());
        data.put("code", 200);
        data.put("message", "success");
        data.put("data", null);
        data.put("timestamp", System.currentTimeMillis());
        data.put("mock", true);

        log.info("生成通用 Mock 数据: {}.{}", request.getInterfaceName(), request.getMethodName());
        return data;
    }

    private Map<String, Object> generateContextMock(String methodName, Map<String, Object> params) {
        Map<String, Object> data = new HashMap<>();
        // 上下文缺失参数诊断
        if ("diagnoseMissingParam".equals(methodName)) {
            String contextId = params != null ? String.valueOf(params.getOrDefault("contextId", "ctx-uuid-123456")) : "ctx-uuid-123456";
            String needCode = params != null ? String.valueOf(params.getOrDefault("needCode", "orderId")) : "orderId";
            data.put("issueReason", "咨询的是非订单相关问题，所以不提供参数订单");
            data.put("contextId", contextId);
            data.put("needCode", needCode);
            log.info("上下文缺失参数诊断 Mock 数据: contextId={}, needCode={}", contextId, needCode);

            // 上下文参数无效诊断
        } else if ("diagnoseInvalidParam".equals(methodName)) {
            String contextId = params != null ? String.valueOf(params.getOrDefault("contextId", "ctx-uuid-123456")) : "ctx-uuid-123456";
            String needCode = params != null ? String.valueOf(params.getOrDefault("needCode", "orderNo")) : "orderNo";

            data.put("issueReason", "订单编号格式不正确，应为10位纯数字，当前输入包含字母");
            data.put("contextId", contextId);
            data.put("needCode", needCode);
            log.info("上下文参数无效诊断 Mock 数据: contextId={}, needCode={}", contextId, needCode);
        } else {
            log.info("生成上下文接口通用 Mock 数据: methodName={}", methodName);
            data.put("methodName", methodName);
            data.put("code", 200);
            data.put("message", "success");
            data.put("data", null);

        }

        return data;
    }

    private Map<String, Object> generateComponentMock(String methodName, Map<String, Object> params) {
        Map<String, Object> data = new HashMap<>();
        if ("diagnoseComponentNotDisplay".equals(methodName)) {
            log.info("组件不展示诊断 Mock 数据: methodName={}", methodName);
            String contextId = params != null ? String.valueOf(params.getOrDefault("contextId", "ctx-uuid-123456")) : "ctx-uuid-123456";
            String componentID = params != null ? String.valueOf(params.getOrDefault("componentID", "comp-payment-001")) : "comp-payment-001";
            data.put("contextId", contextId);
            data.put("componentID", componentID);
            data.put("issueReason", "上下文："+contextId+"缺少参数 orderId,导致支付组件不展示");


        }

        return data;
    }
}