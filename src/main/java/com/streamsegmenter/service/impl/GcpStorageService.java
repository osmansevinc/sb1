package com.streamsegmenter.service.impl;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.streamsegmenter.config.StorageConfig;
import com.streamsegmenter.service.StorageService;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GcpStorageService implements StorageService {
    private final Storage storage;
    private final String bucketName;
    private final String projectId;

    public GcpStorageService(StorageConfig config) {
        this.bucketName = config.getGcpBucket();
        this.projectId = config.getGcpProjectId();
        this.storage = StorageOptions.newBuilder()
            .setProjectId(projectId)
            .build()
            .getService();
    }

    @Override
    public CompletableFuture<String> uploadSegment(Path segmentPath, String streamId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String objectName = String.format("%s/%s", streamId, segmentPath.getFileName());
                BlobId blobId = BlobId.of(bucketName, objectName);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

                storage.create(blobInfo, Files.readAllBytes(segmentPath));
                return getSegmentUrl(streamId, segmentPath.getFileName().toString());
            } catch (Exception e) {
                log.error("Error uploading to GCP: {}", e.getMessage());
                throw new RuntimeException("Failed to upload to GCP", e);
            }
        });
    }

    @Override
    public void deleteStream(String streamId) {
        try {
            storage.list(bucketName, Storage.BlobListOption.prefix(streamId))
                .iterateAll()
                .forEach(blob -> storage.delete(blob.getBlobId()));
        } catch (Exception e) {
            log.error("Error deleting from GCP: {}", e.getMessage());
        }
    }

    @Override
    public String getSegmentUrl(String streamId, String segmentName) {
        return String.format("https://storage.googleapis.com/%s/%s/%s",
            bucketName, streamId, segmentName);
    }
}
