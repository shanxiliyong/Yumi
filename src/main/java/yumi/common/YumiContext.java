package yumi.common;

import lombok.Data;
import yumi.entity.DigitalHumanEntity;
import yumi.request.ChatRequest;

@Data
public class YumiContext {


    private ChatRequest request;

    private DigitalHumanEntity dh;

    private DigitalHumanEntity parentDh;


    public String getSessionKey() {
        String sessionKey = request.getUserId() + "-" +dh.getCode()+"-"+ request.getSessionId();
        return sessionKey;
    }

}