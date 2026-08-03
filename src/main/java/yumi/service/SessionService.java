package yumi.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yumi.entity.SessionEntity;
import yumi.mapper.SessionMapper;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SessionService {

    @Autowired
    private SessionMapper sessionMapper;

    @Transactional
    public Long createSession(String userId, String name, Long digitalHumanId) {
        SessionEntity session = new SessionEntity(userId, name);
        session.setDigitalHumanId(digitalHumanId);
        sessionMapper.insert(session);
        return session.getId();
    }

    @Transactional
    public boolean updateSession(Long id, String name) {
        SessionEntity session = sessionMapper.selectById(id);
        if (session != null) {
            session.setName(name);
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
            return true;
        }
        return false;
    }

    @Transactional
    public boolean deleteSession(Long id) {
        SessionEntity session = sessionMapper.selectById(id);
        if (session != null) {
            sessionMapper.deleteById(id);
            return true;
        }
        return false;
    }

    public SessionEntity getSession(Long id) {
        return sessionMapper.selectById(id);
    }

    public List<SessionEntity> getSessionsByUserId(String userId) {
        return sessionMapper.selectByUserId(userId);
    }

    @Transactional
    public void updateLastMessage(Long id, String lastMessage) {
        sessionMapper.updateLastMessage(id, lastMessage);
    }
}