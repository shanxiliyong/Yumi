package yumi;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;

import java.util.Map;

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