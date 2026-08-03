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
     * 通过事务 + FOR UPDATE 行锁保证并发安全
     */
    @Transactional
    public long nextId(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("code 不能为空");
        }

        // 1. 加行锁查询（不存在时不返回任何行，但也会锁 gap，避免幻读）
        IdGeneratorEntity entity = mapper.selectByCodeForUpdate(code);

        if (entity == null) {
            // 2. 首次调用，插入 value=1
            mapper.insertWithValue1(code);
            return 1L;
        } else {
            // 3. 已存在，+1 后取新值
            mapper.incrementValue(code);
            return mapper.selectValueByCode(code);
        }
    }
}