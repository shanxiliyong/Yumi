package yumi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import yumi.entity.RpcInterfaceEntity;

import java.util.List;

@Mapper
public interface RpcInterfaceMapper extends BaseMapper<RpcInterfaceEntity> {

    @Select("SELECT * FROM rpc_interface WHERE name LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%')")
    IPage<RpcInterfaceEntity> selectPage(Page<RpcInterfaceEntity> page, @Param("keyword") String keyword);

    @Select("SELECT * FROM rpc_interface WHERE enabled = 1")
    List<RpcInterfaceEntity> selectEnabled();

    @Select("SELECT * FROM rpc_interface WHERE interface_name = #{interfaceName} AND method_name = #{methodName}")
    RpcInterfaceEntity selectByInterfaceMethod(@Param("interfaceName") String interfaceName, @Param("methodName") String methodName);
}