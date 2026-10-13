package com.util.demo2.auth;

import com.util.demo2.common.BusinessException;
import com.util.demo2.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 极简 HS256 JWT，自己实现，不引第三方依赖（jjwt 依赖 Jackson 2，而 Boot 4 默认
 * Jackson 3，混用容易出现序列化冲突，所以这里直接手写）。
 *
 * 结构：base64url(header) . base64url(payload) . base64url(HMAC-SHA256 签名)
 *
 * 设计要点：
 * 1. payload 只放 sub(用户id)、iat、exp，不放角色，值全是数字；
 *    角色、禁用状态每次请求都从数据库查，所以"改角色/禁用账号"立即生效，不用等 token 过期。
 * 2. 校验时用 MessageDigest.isEqual 做恒定时比较，避免签名比较被时序攻击。
 */
@Component
public class JwtUtil {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long expireMillis;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-minutes:120}") long expireMinutes) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("app.jwt.secret 长度不能小于 32 个字符，请检查 application.properties");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expireMillis = expireMinutes * 60_000L;
    }

    /** 登录成功后签发 token */
    public String generate(Long userId) {
        long nowSecond = System.currentTimeMillis() / 1000;
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(userId));
        claims.put("iat", nowSecond);
        claims.put("exp", nowSecond + expireMillis / 1000);
        String content = encode(HEADER_JSON) + "." + encode(toJson(claims));
        return content + "." + sign(content);
    }

    /** 校验签名与有效期，返回用户 id；任何异常统一抛 401 */
    public Long parseUserId(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw unauthorized("未登录，请先登录");
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw unauthorized("token 格式错误");
            }
            String content = parts[0] + "." + parts[1];
            byte[] expected = sign(content).getBytes(StandardCharsets.UTF_8);
            byte[] actual = parts[2].getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw unauthorized("token 签名校验失败");
            }
            Map<String, String> claims = parsePayload(new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8));
            String sub = claims.get("sub");
            String exp = claims.get("exp");
            if (sub == null || exp == null) {
                throw unauthorized("token 内容不完整");
            }
            if (Long.parseLong(exp) < System.currentTimeMillis() / 1000) {
                throw unauthorized("登录已过期，请重新登录");
            }
            return Long.parseLong(sub);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw unauthorized("token 无效，请重新登录");
        }
    }

    /** payload 的 key/value 都在可控范围内（数字），因此用一个刻意保守的解析器，不引 JSON 库 */
    private static Map<String, String> parsePayload(String json) {
        Map<String, String> claims = new LinkedHashMap<>();
        String body = json.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            throw new IllegalArgumentException("payload 不是 JSON 对象");
        }
        body = body.substring(1, body.length() - 1).trim();
        if (body.isEmpty()) {
            return claims;
        }
        for (String pair : body.split(",")) {
            int index = pair.indexOf(':');
            if (index < 0) {
                throw new IllegalArgumentException("claim 格式错误");
            }
            claims.put(unquote(pair.substring(0, index)), unquote(pair.substring(index + 1)));
        }
        return claims;
    }

    private static String toJson(Map<String, Object> claims) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number) {
                sb.append(value);
            } else {
                sb.append('"').append(value).append('"');
            }
        }
        return sb.append('}').toString();
    }

    private static String unquote(String text) {
        String value = text.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("token 签名失败", e);
        }
    }

    private static String encode(String text) {
        return ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static BusinessException unauthorized(String msg) {
        return new BusinessException(Result.CODE_UNAUTHORIZED, msg);
    }
}
