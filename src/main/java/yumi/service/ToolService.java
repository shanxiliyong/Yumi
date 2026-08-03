package yumi.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yumi.entity.ToolEntity;
import yumi.mapper.ToolMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ToolService {

    @Autowired
    private ToolMapper toolMapper;

    public Map<String, Object> list(int pageNum, int pageSize, String keyword) {
        Map<String, Object> result = new HashMap<>();
        Page<ToolEntity> page = new Page<>(pageNum, pageSize);
        IPage<ToolEntity> ipage = toolMapper.selectPage(page, keyword);
        result.put("total", ipage.getTotal());
        result.put("records", ipage.getRecords());
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    public List<ToolEntity> listAll() {
        return toolMapper.selectAll();
    }

    public List<ToolEntity> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return toolMapper.selectByIds(ids);
    }

    public List<ToolEntity> listRpcTools() {
        return toolMapper.selectByType("rpc");
    }

    public List<ToolEntity> listSystemTools() {
        return toolMapper.selectByType("system");
    }

    public ToolEntity getById(Long id) {
        return toolMapper.selectById(id);
    }

    public Map<String, Object> create(ToolEntity tool) {
        Map<String, Object> result = new HashMap<>();
        if (tool.getName() == null || tool.getName().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "名称不能为空");
            return result;
        }
        ToolEntity existing = toolMapper.selectByName(tool.getName());
        if (existing != null) {
            result.put("success", false);
            result.put("message", "名称已存在：" + tool.getName());
            return result;
        }
        if (tool.getPermission() == null || tool.getPermission().isEmpty()) {
            tool.setPermission("public");
        }
        tool.setCreateTime(LocalDateTime.now());
        tool.setUpdateTime(LocalDateTime.now());
        toolMapper.insertTool(tool);
        result.put("success", true);
        result.put("message", "创建成功");
        return result;
    }

    public Map<String, Object> update(ToolEntity tool) {
        Map<String, Object> result = new HashMap<>();
        if (tool.getId() == null) {
            result.put("success", false);
            result.put("message", "id 不能为空");
            return result;
        }
        ToolEntity dbEntity = toolMapper.selectById(tool.getId());
        if (dbEntity == null) {
            result.put("success", false);
            result.put("message", "工具不存在");
            return result;
        }
        if (tool.getName() != null && !tool.getName().equals(dbEntity.getName())) {
            ToolEntity byName = toolMapper.selectByName(tool.getName());
            if (byName != null && !byName.getId().equals(tool.getId())) {
                result.put("success", false);
                result.put("message", "名称已存在");
                return result;
            }
        }
        tool.setUpdateTime(LocalDateTime.now());
        toolMapper.updateTool(tool);
        result.put("success", true);
        result.put("message", "更新成功");
        return result;
    }

    public Map<String, Object> delete(Long id) {
        Map<String, Object> result = new HashMap<>();
        ToolEntity dbEntity = toolMapper.selectById(id);
        if (dbEntity == null) {
            result.put("success", false);
            result.put("message", "工具不存在");
            return result;
        }
        toolMapper.deleteById(id);
        result.put("success", true);
        result.put("message", "删除成功");
        return result;
    }
}