package yumi.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yumi.entity.SkillEntity;
import yumi.mapper.SkillMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SkillService {

    @Autowired
    private SkillMapper skillMapper;

    public Map<String, Object> list(int pageNum, int pageSize, String keyword) {
        Map<String, Object> result = new HashMap<>();
        Page<SkillEntity> page = new Page<>(pageNum, pageSize);
        IPage<SkillEntity> ipage = skillMapper.selectPage(page, keyword);
        result.put("total", ipage.getTotal());
        result.put("records", ipage.getRecords());
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    public List<SkillEntity> listAll() {
        return skillMapper.selectAll();
    }

    public List<SkillEntity> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return skillMapper.selectByIds(ids);
    }

    public List<SkillEntity> listEnabled() {
        return skillMapper.selectByStatus(1);
    }

    public SkillEntity getById(Long id) {
        return skillMapper.selectById(id);
    }

    public Map<String, Object> create(SkillEntity skill) {
        Map<String, Object> result = new HashMap<>();
        if (skill.getName() == null || skill.getName().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "名称不能为空");
            return result;
        }
        SkillEntity existing = skillMapper.selectByName(skill.getName());
        if (existing != null) {
            result.put("success", false);
            result.put("message", "名称已存在：" + skill.getName());
            return result;
        }
        if (skill.getVersion() == null || skill.getVersion().isEmpty()) {
            skill.setVersion("v1.0.0");
        }
        if (skill.getStatus() == null) {
            skill.setStatus(1);
        }
        skill.setCreateTime(LocalDateTime.now());
        skill.setUpdateTime(LocalDateTime.now());
        skillMapper.insertSkill(skill);

        result.put("success", true);
        result.put("message", "创建成功");
        return result;
    }

    public Map<String, Object> update(SkillEntity skill) {
        Map<String, Object> result = new HashMap<>();
        if (skill.getId() == null) {
            result.put("success", false);
            result.put("message", "id 不能为空");
            return result;
        }
        SkillEntity dbEntity = skillMapper.selectById(skill.getId());
        if (dbEntity == null) {
            result.put("success", false);
            result.put("message", "技能不存在");
            return result;
        }

        if (skill.getName() != null && !skill.getName().equals(dbEntity.getName())) {
            SkillEntity byName = skillMapper.selectByName(skill.getName());
            if (byName != null && !byName.getId().equals(skill.getId())) {
                result.put("success", false);
                result.put("message", "名称已存在");
                return result;
            }
        }
        skill.setUpdateTime(LocalDateTime.now());
        skillMapper.updateSkill(skill);

        result.put("success", true);
        result.put("message", "更新成功");
        return result;
    }

    public Map<String, Object> delete(Long id) {
        Map<String, Object> result = new HashMap<>();
        SkillEntity dbEntity = skillMapper.selectById(id);
        if (dbEntity == null) {
            result.put("success", false);
            result.put("message", "技能不存在");
            return result;
        }

        skillMapper.deleteById(id);
        result.put("success", true);
        result.put("message", "删除成功");
        return result;
    }
}