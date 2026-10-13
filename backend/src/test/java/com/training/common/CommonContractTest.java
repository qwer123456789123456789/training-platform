package com.training.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 契约自测：校验统一返回体与分页结构的字段值是否正确。
 * 不依赖数据库，纯逻辑测试。
 */
class CommonContractTest {

    @Test
    void resultOkShouldWrapData() {
        Result<String> r = Result.ok("hello");
        assertEquals(200, r.getCode());
        assertEquals("操作成功", r.getMsg());
        assertEquals("hello", r.getData());
    }

    @Test
    void resultFailShouldCarryCodeAndMsg() {
        Result<Void> r = Result.fail(404, "任务不存在");
        assertEquals(404, r.getCode());
        assertEquals("任务不存在", r.getMsg());
        assertNull(r.getData());
    }

    @Test
    void pageResultShouldMatchUnifiedShape() {
        Page<String> page = new Page<>(2, 5);
        page.setRecords(Arrays.asList("a", "b", "c"));
        page.setTotal(13);

        PageResult<String> pr = PageResult.of(page);
        assertEquals(3, pr.getRecords().size());
        assertEquals(13, pr.getTotal());
        assertEquals(2, pr.getCurrent());
        assertEquals(5, pr.getSize());
    }
}
