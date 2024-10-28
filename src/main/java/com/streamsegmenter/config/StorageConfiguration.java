package com.streamsegmenter.config;

import com.streamsegmenter.service.StorageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class StorageConfiguration {
    private final StorageFactory storageFactory;

    @Bean
    public List<StorageService> storageServices() {
        // Get all available storage services based on valid configurations
        return storageFactory.getStorageServices(List.of("LOCAL", "AWS", "AZURE", "GCP"));
    }
}
