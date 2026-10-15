package com.util.demo2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 启动冒烟测试：启动完整 Spring 容器（Controller / Service / Repository / 拦截器 / 事务代理全部装配），
 * 并真实连接 MySQL —— 容器启动时会执行 AdminInitializer，它会查一次数据库确认 admin 账号是否存在。
 *
 * 因此本测试**需要本机 MySQL 已启动**并且完成过建库（database/create-database.sql）。
 * 业务规则与 SQL 的验证分别在：
 *   - UserServiceTest / UserServicePageTest / AuthInterceptorTest / UserApiContractTest（用 Mock，秒级，不连库）
 *   - UserServiceDbTest / UserRepositoryDbTest（真连 MySQL，@Transactional 跑完自动回滚，不在库里留数据）
 */
@SpringBootTest
class Demo2ApplicationTests {

    @Test
    void contextLoads() {
    }

}
