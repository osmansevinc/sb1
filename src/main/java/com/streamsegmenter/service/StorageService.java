package com.streamsegmenter.service;

import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface StorageService {
    CompletableFuture<String> uploadSegment(Path segmentPath, String streamId);
    void deleteStream(String streamId);
    String getSegmentUrl(String streamId, String segmentName);
    String getAdvertisementUrl(String streamId, String segmentName);
    default String getStorageType() {
        return this.getClass().getSimpleName().replace("StorageService", "").toUpperCase();
    }
    // Add method to delete individual segments
    default void deleteSegment(String streamId, String segmentName) {
        // Default implementation does nothing
        // Each storage service should implement this if needed
    }
}
