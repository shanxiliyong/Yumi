package demo;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BiFunctionDemo {
    public static void main(String[] args) {
        // 步骤1: 定义一个 BiFunction - 计算两个数的和
        BiFunction<Integer, Integer, Integer> add = (a, b) -> {
            System.out.println("执行 add: " + a + " + " + b);
            return a + b;
        };

        // 步骤2: 定义一个后续 Function - 将结果平方
        Function<Integer, Integer> square = x -> {
            System.out.println("执行 square: " + x + " ^ 2");
            return x * x;
        };

        // 步骤3: 使用 andThen 组合
        BiFunction<Integer, Integer, Integer> addThenSquare = add.andThen(square);

        // 步骤4: 执行组合函数
        int result = addThenSquare.apply(3, 5);
        System.out.println("最终结果: " + result);
    }
}
