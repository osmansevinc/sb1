package com.streamsegmenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;
import java.nio.file.Path;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {
    private List<String> types; // Support multiple storage types
    private String awsAccessKey;
    private String awsSecretKey;
    private String awsBucket;
    private String azureConnectionString;
    private String azureContainer;
    private String gcpProjectId;
    private String gcpBucket;
    private String localTempPath;
    private String serverUrl = "http://localhost:8080"; // Default server URL

    public String getEffectiveTempPath() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return "C:\\temp";
        }
        return localTempPath;
    }

    public Path resolvePath(String... parts) {
        return Path.of(getEffectiveTempPath(), parts);
    }
}
