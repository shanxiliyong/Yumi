package yumi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yumi.entity.IdGeneratorEntity;

@Mapper
public interface IdGeneratorMapper extends BaseMapper<IdGeneratorEntity> {

    /**
     * value = value + 1
     */
    int incrementValue(@Param("code") String code);

    /**
     * 首次插入：code + value=1
     */
    int insertWithValue1(@Param("code") String code);

    /**
     * 取当前值
     */
    Long selectValueByCode(@Param("code") String code);
}