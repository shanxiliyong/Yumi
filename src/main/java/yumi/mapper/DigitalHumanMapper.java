package yumi.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yumi.entity.DigitalHumanEntity;

import java.util.List;

@Mapper
public interface DigitalHumanMapper {

    DigitalHumanEntity selectByCode(@Param("code") String code);

    DigitalHumanEntity selectByName(@Param("name") String name);

    List<DigitalHumanEntity> selectAll();

    IPage<DigitalHumanEntity> selectPage(Page<DigitalHumanEntity> page, @Param("keyword") String keyword);

    IPage<DigitalHumanEntity> selectChildPage(Page<DigitalHumanEntity> page, @Param("parentCode") String parentCode, @Param("keyword") String keyword);

    void insertDigitalHuman(DigitalHumanEntity entity);

    void updateDigitalHuman(DigitalHumanEntity entity);

    void deleteById(@Param("id") Long id);

    DigitalHumanEntity selectById(@Param("id") Long id);

    int countChildrenByParentCode(@Param("parentCode") String parentCode);

    List<DigitalHumanEntity> selectChildrenByParentCode(@Param("parentCode") String parentCode);
}