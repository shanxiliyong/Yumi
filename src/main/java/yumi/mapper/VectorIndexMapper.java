package yumi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import yumi.entity.VectorIndexEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 向量索引 Mapper
 */
@Mapper
public interface VectorIndexMapper extends BaseMapper<VectorIndexEntity> {
}