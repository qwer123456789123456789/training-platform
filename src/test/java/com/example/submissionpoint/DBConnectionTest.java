package com.example.submissionpoint;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import javax.sql.DataSource;
import java.sql.Connection;

@SpringBootTest   // 你这里漏掉这个注解！不加无法注入dataSource
public class DBConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void testConnectMysql() throws Exception {
        Connection connection = dataSource.getConnection();
        System.out.println("✅ 数据库连接成功！连接对象：" + connection);
        connection.close();
    }
}