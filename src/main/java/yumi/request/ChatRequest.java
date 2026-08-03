package yumi.request;

import lombok.Data;

@Data
public class ChatRequest {

    private String userId;

    private Long sessionId;

    private String message;
}