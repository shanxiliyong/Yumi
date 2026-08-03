package yumi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import yumi.entity.SessionEntity;

import java.util.List;

@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {
    
    List<SessionEntity> selectByUserId(String userId);
    
    int updateLastMessage(Long id, String lastMessage);
}