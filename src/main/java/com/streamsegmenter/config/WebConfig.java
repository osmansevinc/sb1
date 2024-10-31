package com.streamsegmenter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final StorageConfig storageConfig;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Stream segments için mapping
        registry.addResourceHandler("/streams/**")
                .addResourceLocations("file:" + storageConfig.getEffectiveTempPath() + "/streams/");

        // Advertisement segments için ayrı mapping
        registry.addResourceHandler("/advertisements/**")
                .addResourceLocations("file:" + storageConfig.getEffectiveTempPath() + "/advertisements/");
    }
}
