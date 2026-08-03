package yumi.common;


/**
 * 错误消息异常
 */
public class YErrorMessageException extends RuntimeException {
    public YErrorMessageException(String message) {
        super(message);
    }
}
