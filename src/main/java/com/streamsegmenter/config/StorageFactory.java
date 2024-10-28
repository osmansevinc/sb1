package com.streamsegmenter.config;

import com.streamsegmenter.service.StorageService;
import com.streamsegmenter.service.impl.*;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import java.util.*;

@Component
@RequiredArgsConstructor
public class StorageFactory {
    private final StorageConfig config;
    
    public List<StorageService> getStorageServices(List<String> requestedTypes) {
        List<StorageService> services = new ArrayList<>();
        Set<String> supportedTypes = new HashSet<>();
        
        // Always add LOCAL as base storage
        services.add(new LocalStorageService(config));
        supportedTypes.add("LOCAL");
        
        // Check AWS configuration
        if (StringUtils.hasText(config.getAwsAccessKey()) && 
            StringUtils.hasText(config.getAwsSecretKey()) && 
            StringUtils.hasText(config.getAwsBucket())) {
            services.add(new AwsStorageService(config));
            supportedTypes.add("AWS");
        }
        
        // Check Azure configuration
        if (StringUtils.hasText(config.getAzureConnectionString()) && 
            StringUtils.hasText(config.getAzureContainer())) {
            services.add(new AzureStorageService(config));
            supportedTypes.add("AZURE");
        }
        
        // Check GCP configuration
        if (StringUtils.hasText(config.getGcpProjectId()) && 
            StringUtils.hasText(config.getGcpBucket())) {
            services.add(new GcpStorageService(config));
            supportedTypes.add("GCP");
        }
        
        // Filter services based on requested types if provided
        if (requestedTypes != null && !requestedTypes.isEmpty()) {
            return services.stream()
                .filter(service -> {
                    String serviceType = service.getClass().getSimpleName()
                        .replace("StorageService", "")
                        .toUpperCase();
                    return requestedTypes.contains(serviceType);
                })
                .toList();
        }
        
        return services;
    }
}