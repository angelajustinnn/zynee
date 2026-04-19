package com.zynee.zynee.config;

import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadDir = Paths.get("uploads/profile-pics/").toAbsolutePath().toUri().toString();
        System.out.println("🛠 Resource handler mapping: " + uploadDir);

        registry.addResourceHandler("/profile-pics/**")
                .addResourceLocations(uploadDir);
    }
}
