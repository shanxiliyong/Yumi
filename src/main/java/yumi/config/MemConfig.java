package yumi.config;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * @author YiHui
 * @date 2025/8/6
 */
@Configuration
public class MemConfig {




    @Bean
    public MysqlSaver mysqlSaver(DataSource dataSource) {
        return MysqlSaver.builder()
                // 如果表不存在则自动创建，存在则复用（安全通用）
                .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                .stateSerializer(new SpringAIJacksonStateSerializer(OverAllState::new))
//                .stateSerializer(new YumiStateSerializer(OverAllState::new))
                // 注入 Spring 自动配置的数据源
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public RedisSaver redisSaver(DataSource dataSource) {
        // 配置 Redisson 客户端
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://localhost:6379");  // Redis 地址

        RedissonClient redisson = Redisson.create(config);
        return RedisSaver.builder().redisson(redisson).build();
    }


}
