package yumi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yumi.entity.IdGeneratorEntity;

@Mapper
public interface IdGeneratorMapper extends BaseMapper<IdGeneratorEntity> {

    /**
     * 按 code 查询（FOR UPDATE 行锁，保证并发安全）
     */
    IdGeneratorEntity selectByCodeForUpdate(@Param("code") String code);

    /**
     * 首次插入：code + value=1
     */
    int insertWithValue1(@Param("code") String code);

    /**
     * value = value + 1，并返回新值（单次操作，避免 FOR UPDATE 行锁）
     */
    Long incrementAndGet(@Param("code") String code);

    /**
     * 取更新后的值（已废弃，保留兼容）
     */
    Long selectValueByCode(@Param("code") String code);
}