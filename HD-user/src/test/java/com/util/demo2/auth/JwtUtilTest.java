package com.util.demo2.auth;

import com.util.demo2.common.BusinessException;
import com.util.demo2.common.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtUtil 单元测试：不需要 Spring 容器，也不需要数据库，可单独跑。
 * 运行：mvnw.cmd test -Dtest=JwtUtilTest
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-0123456789abcdef";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 120);

    @Test
    @DisplayName("正常签发后能解析出同一个用户 id")
    void generateThenParse() {
        String token = jwtUtil.generate(42L);
        assertEquals(42L, jwtUtil.parseUserId(token).longValue());
    }

    @Test
    @DisplayName("token 是三段式结构 header.payload.signature")
    void tokenHasThreeParts() {
        assertEquals(3, jwtUtil.generate(1L).split("\\.").length);
    }

    @Test
    @DisplayName("空 token 返回 401")
    void blankTokenRejected() {
        BusinessException e = assertThrows(BusinessException.class, () -> jwtUtil.parseUserId(null));
        assertEquals(Result.CODE_UNAUTHORIZED, e.getCode());
        assertThrows(BusinessException.class, () -> jwtUtil.parseUserId("   "));
    }

    @Test
    @DisplayName("格式错误的 token 返回 401")
    void malformedTokenRejected() {
        assertThrows(BusinessException.class, () -> jwtUtil.parseUserId("abc"));
        assertThrows(BusinessException.class, () -> jwtUtil.parseUserId("a.b.c"));
    }

    @Test
    @DisplayName("payload 被篡改（签名对不上）返回 401")
    void tamperedPayloadRejected() {
        String token = jwtUtil.generate(1L);
        String[] parts = token.split("\\.");
        String fakePayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"999\",\"iat\":1,\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + fakePayload + "." + parts[2];

        BusinessException e = assertThrows(BusinessException.class, () -> jwtUtil.parseUserId(tampered));
        assertEquals(Result.CODE_UNAUTHORIZED, e.getCode());
    }

    @Test
    @DisplayName("用别的密钥签发的 token 被拒绝")
    void tokenFromAnotherSecretRejected() {
        JwtUtil other = new JwtUtil("another-secret-key-0123456789abcdefgh", 120);
        String token = other.generate(1L);
        assertThrows(BusinessException.class, () -> jwtUtil.parseUserId(token));
    }

    @Test
    @DisplayName("过期 token 被拒绝并提示重新登录")
    void expiredTokenRejected() {
        JwtUtil expiredJwt = new JwtUtil(SECRET, -1); // 负数有效期 => exp 在过去
        String token = expiredJwt.generate(1L);

        BusinessException e = assertThrows(BusinessException.class, () -> jwtUtil.parseUserId(token));
        assertTrue(e.getMessage().contains("过期"));
    }

    @Test
    @DisplayName("密钥长度不足 32 位时启动即报错")
    void shortSecretRejected() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("too-short", 120));
    }
}
