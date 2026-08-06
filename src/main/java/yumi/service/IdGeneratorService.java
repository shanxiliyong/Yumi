package yumi.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yumi.entity.IdGeneratorEntity;
import yumi.mapper.IdGeneratorMapper;

@Service
public class IdGeneratorService {

    @Autowired
    private IdGeneratorMapper mapper;

    /**
     * 获取下一个 ID
     * - 首次调用（code 不存在）：插入一条 value=1 的记录，返回 1
     * - 后续每次调用：value + 1，返回新值
     * 通过单次 UPDATE 语句直接返回新值，避免 FOR UPDATE 行锁性能问题
     */
    @Transactional
    public long nextId(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("code 不能为空");
        }

        // 单次 UPDATE 直接返回新值（影响行数为 0 说明记录不存在）
        Long newValue = mapper.incrementAndGet(code);
        if (newValue != null) {
            return newValue;
        }

        // 首次调用，插入 value=1
        mapper.insertWithValue1(code);
        return 1L;
    }
}