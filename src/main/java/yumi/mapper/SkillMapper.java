package yumi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yumi.entity.SkillEntity;

import java.util.List;

@Mapper
public interface SkillMapper extends BaseMapper<SkillEntity> {

    SkillEntity selectByName(@Param("name") String name);

    List<SkillEntity> selectAll();

    List<SkillEntity> selectByIds(@Param("ids") List<Long> ids);

    List<SkillEntity> selectByStatus(@Param("status") Integer status);

    IPage<SkillEntity> selectPage(Page<SkillEntity> page, @Param("keyword") String keyword);

    int insertSkill(SkillEntity skill);

    int updateSkill(SkillEntity skill);

    int deleteById(@Param("id") Long id);
}