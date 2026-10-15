package com.example.submissionpoint.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("成果提交打分接口")
                        .version("1.0")
                        .description("学生上传文件、教师打分、锁定提交记录")
                        .contact(new Contact().name("后端3")));
    }
}