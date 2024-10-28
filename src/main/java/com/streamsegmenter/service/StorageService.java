package com.streamsegmenter.service;

import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface StorageService {
    CompletableFuture<String> uploadSegment(Path segmentPath, String streamId);
    void deleteStream(String streamId);
    String getSegmentUrl(String streamId, String segmentName);
    default String getStorageType() {
        return this.getClass().getSimpleName().replace("StorageService", "").toUpperCase();
    }
}
