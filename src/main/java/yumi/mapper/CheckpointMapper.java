package yumi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yumi.entity.CheckpointEntity;

import java.util.List;

@Mapper
public interface CheckpointMapper extends BaseMapper<CheckpointEntity> {

    List<CheckpointEntity> selectByThreadIdOrderBySeq(@Param("threadId") String threadId);

    CheckpointEntity selectLatestByThreadId(@Param("threadId") String threadId);
}