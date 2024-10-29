package com.streamsegmenter.service.impl;

import com.streamsegmenter.config.StorageConfig;
import com.streamsegmenter.service.StorageService;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {
    private final StorageConfig config;

    @Override
    public CompletableFuture<String> uploadSegment(Path segmentPath, String streamId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path targetDir = config.resolvePath(streamId);
                Files.createDirectories(targetDir);

                Path targetPath = targetDir.resolve(segmentPath.getFileName());
                Files.copy(segmentPath, targetPath, StandardCopyOption.REPLACE_EXISTING);

                return getSegmentUrl(streamId, targetPath.getFileName().toString());
            } catch (Exception e) {
                log.error("Error uploading segment: {}", e.getMessage());
                throw new RuntimeException("Failed to upload segment", e);
            }
        });
    }

    @Override
    public void deleteStream(String streamId) {
        try {
            Path streamDir = config.resolvePath("streams", streamId);
            Files.walk(streamDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            log.error("Error deleting file: {}", e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error deleting stream: {}", e.getMessage());
        }
    }

    @Override
    public String getSegmentUrl(String streamId, String segmentName) {
        return String.format("%s/streams/streams/%s/%s", config.getServerUrl(), streamId, segmentName);
    }
}
