package yumi.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Configuration
public class StrategyConfig {

    @Value("${strategy.config.path:classpath:config/strategies.json}")
    private String configPath;

    private List<Map<String, Object>> strategies;

    private final ObjectMapper objectMapper;

    public StrategyConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() throws Exception {
        if (configPath.startsWith("classpath:")) {
            String resourcePath = configPath.substring("classpath:".length());
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    strategies = objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
                }
            }
        }
    }

    public List<Map<String, Object>> getStrategies() {
        return strategies;
    }

    public Map<String, Object> getStrategyById(String strategyId) {
        return strategies.stream()
                .filter(s -> strategyId.equals(s.get("id")))
                .findFirst()
                .orElse(null);
    }

    public String getInstruction(String strategyId) {
        Map<String, Object> strategy = getStrategyById(strategyId);
        if (strategy != null) {
            return (String) strategy.get("instruction");
        }
        return "你是Yumi，一个友好、热情的AI助手。善于倾听用户的需求，提供贴心的帮助和建议。";
    }

    public String getRequestType(String strategyId) {
        Map<String, Object> strategy = getStrategyById(strategyId);
        if (strategy != null) {
            return (String) strategy.get("requestType");
        }
        return "stream";
    }

    public String getBeanName(String strategyId) {
        Map<String, Object> strategy = getStrategyById(strategyId);
        if (strategy != null) {
            return (String) strategy.get("beanName");
        }
        return "reactAgent";
    }
}