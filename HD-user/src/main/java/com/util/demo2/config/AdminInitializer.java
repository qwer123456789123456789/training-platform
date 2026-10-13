package com.util.demo2.config;

import com.util.demo2.auth.LoginUser;
import com.util.demo2.user.entity.User;
import com.util.demo2.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时插入一个可用的管理员账号，避免"连登录都登不进去"。
 * 由 app.init-admin 开关控制，初始化完成后建议改成 false。
 *
 * 因为 BCrypt 每次加盐结果都不同，没法在 SQL 里写死哈希值，所以放在代码里初始化。
 */
@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;

    public AdminInitializer(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${app.init-admin:false}") boolean enabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (userRepository.findByUsername(DEFAULT_USERNAME).isPresent()) {
            log.info("已存在 {} 账号，跳过初始化", DEFAULT_USERNAME);
            return;
        }
        User admin = new User();
        admin.setUsername(DEFAULT_USERNAME);
        admin.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        admin.setRealName("系统管理员");
        admin.setRole(LoginUser.ROLE_ADMIN);
        admin.setStatus(1);
        userRepository.insert(admin);
        log.warn("已初始化管理员账号：{} / {}，请登录后立即修改密码", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }
}
