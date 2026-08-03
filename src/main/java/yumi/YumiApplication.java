package yumi;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;

@Controller
@SpringBootApplication
public class YumiApplication {

    public static void main(String[] args) {
        SpringApplication.run(YumiApplication.class, args);
    }

    @Autowired
    private ChatModel chatModel;

    private ReactAgent supervisorAgent;



    @Bean
    CommandLineRunner commandLineRunner(ChatModel chatModel) {
        return args -> {
            System.out.println();
            System.out.println("\n=== Yumi 服务已启动 ===");
        };
    }
}