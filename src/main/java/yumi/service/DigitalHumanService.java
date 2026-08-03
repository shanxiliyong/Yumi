package yumi.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import yumi.entity.DigitalHumanEntity;
import yumi.mapper.DigitalHumanMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DigitalHumanService {

    @Autowired
    private DigitalHumanMapper digitalHumanMapper;

    public Map<String, Object> list(int pageNum, int pageSize, String keyword) {
        Map<String, Object> result = new HashMap<>();
        Page<DigitalHumanEntity> page = new Page<>(pageNum, pageSize);
        IPage<DigitalHumanEntity> iPage = digitalHumanMapper.selectPage(page, keyword);
        result.put("records", iPage.getRecords());
        result.put("total", iPage.getTotal());
        return result;
    }

    public Map<String, Object> listChildren(int pageNum, int pageSize, String parentCode, String keyword) {
        Map<String, Object> result = new HashMap<>();
        Page<DigitalHumanEntity> page = new Page<>(pageNum, pageSize);
        IPage<DigitalHumanEntity> iPage = digitalHumanMapper.selectChildPage(page, parentCode, keyword);
        result.put("records", iPage.getRecords());
        result.put("total", iPage.getTotal());
        return result;
    }

    public List<DigitalHumanEntity> listAll() {
        return digitalHumanMapper.selectAll();
    }

    public DigitalHumanEntity getById(Long id) {
        return digitalHumanMapper.selectById(id);
    }

    public DigitalHumanEntity getByCode(String code) {
        return digitalHumanMapper.selectByCode(code);
    }

    public List<DigitalHumanEntity> listChildrenByParentCode(String parentCode) {
        return digitalHumanMapper.selectChildrenByParentCode(parentCode);
    }

    @Transactional
    public Map<String, Object> create(DigitalHumanEntity entity) {
        Map<String, Object> result = new HashMap<>();
        if (entity.getCode() == null || entity.getCode().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "code 不能为空");
            return result;
        }
        if (entity.getName() == null || entity.getName().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "名称不能为空");
            return result;
        }
        DigitalHumanEntity existing = digitalHumanMapper.selectByCode(entity.getCode());
        if (existing != null) {
            result.put("success", false);
            result.put("message", "code 已存在：" + entity.getCode());
            return result;
        }
        if (!StringUtils.hasText(entity.getAgentType())) {
            entity.setAgentType("parent");
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getMultiAgentEnabled() == null) {
            entity.setMultiAgentEnabled(0);
        }
        if (entity.getStreamingEnabled() == null) {
            entity.setStreamingEnabled(0);
        }
        digitalHumanMapper.insertDigitalHuman(entity);
        result.put("success", true);
        result.put("message", "创建成功");
        result.put("data", entity);
        return result;
    }

    @Transactional
    public Map<String, Object> update(DigitalHumanEntity entity) {
        Map<String, Object> result = new HashMap<>();
        if (entity.getId() == null) {
            result.put("success", false);
            result.put("message", "ID不能为空");
            return result;
        }
        DigitalHumanEntity dbEntity = digitalHumanMapper.selectById(entity.getId());
        if (dbEntity == null) {
            result.put("success", false);
            result.put("message", "记录不存在");
            return result;
        }
        if (entity.getCode() != null && !entity.getCode().equals(dbEntity.getCode())) {
            DigitalHumanEntity byCode = digitalHumanMapper.selectByCode(entity.getCode());
            if (byCode != null && !byCode.getId().equals(entity.getId())) {
                result.put("success", false);
                result.put("message", "code 已存在：" + entity.getCode());
                return result;
            }
        }
        entity.setUpdateTime(LocalDateTime.now());
        digitalHumanMapper.updateDigitalHuman(entity);
        result.put("success", true);
        result.put("message", "更新成功");
        return result;
    }

    @Transactional
    public Map<String, Object> delete(Long id) {
        Map<String, Object> result = new HashMap<>();
        DigitalHumanEntity dbEntity = digitalHumanMapper.selectById(id);
        if (dbEntity == null) {
            result.put("success", false);
            result.put("message", "记录不存在");
            return result;
        }
        if ("parent".equals(dbEntity.getAgentType())) {
            int childCount = digitalHumanMapper.countChildrenByParentCode(dbEntity.getCode());
            if (childCount > 0) {
                result.put("success", false);
                result.put("message", "请先删除该数字人下的所有子 Agent");
                return result;
            }
        }
        digitalHumanMapper.deleteById(id);
        result.put("success", true);
        result.put("message", "删除成功");
        return result;
    }
}