package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CompletableFutureDemo {

    public static void main(String[] args) throws Exception {
        // 创建异步任务
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            // 模拟耗时操作
            try {
                System.out.println("异步任务开始执行...");
                Thread.sleep(2000);
                System.out.println("异步任务执行完成...");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Hello CompletableFuture";
        });

        // 主线程继续执行其他任务
        System.out.println("主线程继续执行...");

        // 获取异步结果（阻塞等待）
        String result = future.get();
        System.out.println("异步结果: " + result);
    }


}
