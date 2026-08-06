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
     * 无行锁，通过 UPDATE 影响行数判断记录是否存在
     */
    @Transactional
    public long nextId(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("code 不能为空");
        }

        // 先尝试 UPDATE（无行锁）
        int rows = mapper.incrementValue(code);
        if (rows > 0) {
            // 记录存在，返回新值
            return mapper.selectValueByCode(code);
        }

        // 记录不存在，首次插入
        mapper.insertWithValue1(code);
        return 1L;
    }
}