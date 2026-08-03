package yumi.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP 客户端配置类
 * 设置 REST 请求超时时间为 1 分钟
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                // 连接超时：1分钟
                .connectTimeout(Duration.ofMinutes(1))
                // 读取超时：1分钟
                .readTimeout(Duration.ofMinutes(1))
                .build();
    }
}