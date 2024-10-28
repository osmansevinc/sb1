package com.streamsegmenter.service.impl;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.streamsegmenter.config.StorageConfig;
import com.streamsegmenter.service.StorageService;
import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AzureStorageService implements StorageService {
    private final BlobContainerClient containerClient;
    private final String containerName;

    public AzureStorageService(StorageConfig config) {
        this.containerName = config.getAzureContainer();
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
            .connectionString(config.getAzureConnectionString())
            .buildClient();
        this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
    }

    @Override
    public CompletableFuture<String> uploadSegment(Path segmentPath, String streamId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String blobName = String.format("%s/%s", streamId, segmentPath.getFileName());
                containerClient.getBlobClient(blobName)
                    .uploadFromFile(segmentPath.toString(), true);

                return getSegmentUrl(streamId, segmentPath.getFileName().toString());
            } catch (Exception e) {
                log.error("Error uploading to Azure: {}", e.getMessage());
                throw new RuntimeException("Failed to upload to Azure", e);
            }
        });
    }

    @Override
    public void deleteStream(String streamId) {
        try {
            containerClient.listBlobs()
                .stream()
                .filter(item -> item.getName().startsWith(streamId))
                .forEach(item -> containerClient.getBlobClient(item.getName()).delete());
        } catch (Exception e) {
            log.error("Error deleting from Azure: {}", e.getMessage());
        }
    }

    @Override
    public String getSegmentUrl(String streamId, String segmentName) {
        return containerClient.getBlobClient(String.format("%s/%s", streamId, segmentName))
            .getBlobUrl();
    }
}
