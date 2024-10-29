package com.streamsegmenter.service.impl;

import com.streamsegmenter.config.StorageConfig;
import com.streamsegmenter.service.StorageService;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AwsStorageService implements StorageService {
    private final S3Client s3Client;
    private final String bucket;
    private static final int MAX_RETRIES = 3;
    private static final int WAIT_TIME_MS = 500;

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
            int retries = 0;
            Exception lastException = null;

            while (retries < MAX_RETRIES) {
                try {
                    // Wait for file to be completely written
                    Thread.sleep(WAIT_TIME_MS * (retries + 1));

                    if (!Files.exists(segmentPath)) {
                        throw new RuntimeException("File does not exist: " + segmentPath);
                    }

                    long fileSize = Files.size(segmentPath);
                    if (fileSize == 0) {
                        throw new RuntimeException("File is empty: " + segmentPath);
                    }

                    byte[] fileContent = Files.readAllBytes(segmentPath);
                    String key = String.format("%s/%s", streamId, segmentPath.getFileName());

                    PutObjectRequest request = PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build();

                    s3Client.putObject(request, RequestBody.fromBytes(fileContent));
                    log.info("Successfully uploaded segment to S3: {} (size: {} bytes)", key, fileSize);
                    return getSegmentUrl(streamId, segmentPath.getFileName().toString());
                } catch (Exception e) {
                    lastException = e;
                    log.warn("Retry {}/{} - Error uploading to S3: {} - {}",
                            retries + 1, MAX_RETRIES, segmentPath, e.getMessage());
                    retries++;
                }
            }

            log.error("Failed to upload after {} retries: {} - {}",
                    MAX_RETRIES, segmentPath, lastException.getMessage());
            throw new RuntimeException("Failed to upload to S3 after " + MAX_RETRIES + " retries", lastException);
        });
    }

    @Override
    public void deleteStream(String streamId) {
        try {
            String prefix = streamId + "/";
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listResponse;
            do {
                listResponse = s3Client.listObjectsV2(listRequest);
                for (S3Object object : listResponse.contents()) {
                    try {
                        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                                .bucket(bucket)
                                .key(object.key())
                                .build();

                        s3Client.deleteObject(deleteRequest);
                        log.info("Deleted S3 object: {}", object.key());
                    } catch (Exception e) {
                        log.error("Error deleting object {}: {}", object.key(), e.getMessage());
                    }
                }

                String token = listResponse.nextContinuationToken();
                if (token == null) {
                    break;
                }

                listRequest = ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .continuationToken(token)
                        .build();

            } while (listResponse.isTruncated());

        } catch (Exception e) {
            log.error("Error deleting stream from S3: {}", e.getMessage());
            throw new RuntimeException("Failed to delete from S3", e);
        }
    }

    @Override
    public String getSegmentUrl(String streamId, String segmentName) {
        return String.format("https://%s.s3.amazonaws.com/%s/%s",
                bucket, streamId, segmentName);
    }
}
