package com.streamsegmenter.service.impl;

import com.streamsegmenter.config.StorageConfig;
import com.streamsegmenter.service.StorageService;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AwsStorageService implements StorageService {
    private final S3Client s3Client;
    private final String bucket;

    public AwsStorageService(StorageConfig config) {
        this.bucket = config.getAwsBucket();
        this.s3Client = S3Client.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(() -> AwsBasicCredentials.create(
                config.getAwsAccessKey(),
                config.getAwsSecretKey()))
            .build();
    }

    @Override
    public CompletableFuture<String> uploadSegment(Path segmentPath, String streamId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String key = String.format("%s/%s", streamId, segmentPath.getFileName());
                s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build(),
                    segmentPath);

                return getSegmentUrl(streamId, segmentPath.getFileName().toString());
            } catch (Exception e) {
                log.error("Error uploading to S3: {}", e.getMessage());
                throw new RuntimeException("Failed to upload to S3", e);
            }
        });
    }

    @Override
    public void deleteStream(String streamId) {
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(streamId)
                .build();

            s3Client.listObjectsV2(listRequest).contents().forEach(object -> {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(object.key())
                    .build());
            });
        } catch (Exception e) {
            log.error("Error deleting from S3: {}", e.getMessage());
        }
    }

    @Override
    public String getSegmentUrl(String streamId, String segmentName) {
        return String.format("https://%s.s3.amazonaws.com/%s/%s",
            bucket, streamId, segmentName);
    }
}
