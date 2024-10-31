package com.streamsegmenter.service.impl;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Cors;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.ImmutableList;
import com.streamsegmenter.config.StorageConfig;
import com.streamsegmenter.service.StorageService;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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

        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream serviceAccountStream = classLoader.getResourceAsStream("plasma-envoy-440206-j7-e9f5b61bf9f8.json")) {
            if (serviceAccountStream == null) {
                throw new FileNotFoundException("Service account key file not found in resources folder");
            }
            this.storage = StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .setCredentials(ServiceAccountCredentials.fromStream(serviceAccountStream))
                    .build()
                    .getService();
            configureBucketCors(bucketName, "Content-Type", 3600);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private void configureBucketCors(String bucketName, String responseHeader, Integer maxAgeSeconds) {
        Bucket bucket = storage.get(bucketName);

        Cors cors = Cors.newBuilder()
                .setOrigins(ImmutableList.of(Cors.Origin.of("*")))  // Allow all origins
                .setMethods(ImmutableList.of(HttpMethod.GET))
                .setResponseHeaders(ImmutableList.of(responseHeader))
                .setMaxAgeSeconds(maxAgeSeconds)
                .build();

        // Update the bucket with the new CORS configuration
        bucket.toBuilder().setCors(ImmutableList.of(cors)).build().update();

        log.info("Bucket {} was updated with a CORS config to allow GET requests from all origins sharing {} responses across origins",
                bucketName, responseHeader);
    }

    @Override
    public void deleteSegment(String streamId, String segmentName) {
        try {
            String objectName = String.format("%s/%s", streamId, segmentName);
            BlobId blobId = BlobId.of(bucketName, objectName);
            storage.delete(blobId);
            log.info("Deleted segment from GCP: {}", objectName);
        } catch (Exception e) {
            log.error("Error deleting segment from GCP: {}", e.getMessage());
        }
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

    @Override
    public String getAdvertisementUrl(String streamId, String segmentName) {
        return null;
    }
}
