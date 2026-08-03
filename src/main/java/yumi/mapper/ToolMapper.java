package yumi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yumi.entity.ToolEntity;

import java.util.List;

@Mapper
public interface ToolMapper extends BaseMapper<ToolEntity> {

    ToolEntity selectByName(@Param("name") String name);

    List<ToolEntity> selectAll();

    IPage<ToolEntity> selectPage(Page<ToolEntity> page, @Param("keyword") String keyword);

    List<ToolEntity> selectByType(@Param("type") String type);

    List<ToolEntity> selectByIds(@Param("ids") List<Long> ids);

    int insertTool(ToolEntity tool);

    int updateTool(ToolEntity tool);

    int deleteById(@Param("id") Long id);
}