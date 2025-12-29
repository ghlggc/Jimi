package io.leavesfly.jimi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Jimi 应用主启动类
 */
@SpringBootApplication
@ConfigurationPropertiesScan("io.leavesfly.jimi.config")
public class JimiApplication {

    public static void main(String[] args) {
        // 禁用 Spring Boot banner，使用自定义输出
        SpringApplication app = new SpringApplication(JimiApplication.class);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.run(args);
    }
}
