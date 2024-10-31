package com.streamsegmenter.service;

import com.streamsegmenter.config.StorageConfig;
import com.streamsegmenter.model.AdvertisementRequest;
import com.streamsegmenter.service.impl.LocalStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvertisementService {
    private final StorageConfig config;
    private final FFmpegService ffmpegService;
    private final M3u8Service m3u8Service;
    private final StorageManager storageManager;
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> streamAdvertisements = new ConcurrentHashMap<>();

    public void insertAdvertisement(AdvertisementRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            Path tempFile = Files.createTempFile("ad-", getExtension(request.getFile().getOriginalFilename()));
            request.getFile().transferTo(tempFile.toFile());

            Path outputDir = config.resolvePath("advertisements", request.getStreamId());
            Files.createDirectories(outputDir);

            CompletableFuture<Void> processingFuture;

            switch (request.getType()) {
                case IMAGE:
                    processingFuture = processImage(tempFile, outputDir, request);
                    break;
                case VIDEO:
                    processingFuture = processVideo(tempFile, outputDir, request);
                    break;
                case TS_FILE:
                    processingFuture = processTs(tempFile, outputDir, request);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported advertisement type");
            }

            processingFuture.thenRun(() -> {
                try {
                    // Upload advertisement to all active storages
                    uploadAdvertisementToStorages(request.getStreamId(), outputDir, request.getStartSegment());
                    registerAdvertisement(request.getStreamId(), request.getStartSegment(), outputDir.toString());
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Advertisement processing completed in {} ms", duration);
                } catch (Exception e) {
                    log.error("Failed to upload advertisement", e);
                }
            }).exceptionally(e -> {
                log.error("Failed to process advertisement", e);
                return null;
            });

        } catch (Exception e) {
            log.error("Failed to insert advertisement", e);
            throw new RuntimeException("Advertisement insertion failed", e);
        }
    }

    private void uploadAdvertisementToStorages(String streamId, Path adDir, int segmentNumber) {
        List<StorageService> services = storageManager.getStoragesForStream(streamId);
        String adSegmentName = "advertisement_" + segmentNumber + ".ts";
        Path adSegmentPath = adDir.resolve(adSegmentName);

        if (!Files.exists(adSegmentPath)) {
            log.warn("Advertisement segment not found: {}", adSegmentPath);
            return;
        }

        for (StorageService service : services) {
            if (!(service instanceof LocalStorageService)) { // Local storage already has the file
                try {
                    service.uploadSegment(adSegmentPath, streamId).get(); // Wait for upload
                    log.info("Advertisement uploaded to storage: {}", service.getStorageType());
                } catch (Exception e) {
                    log.error("Failed to upload advertisement to storage {}: {}",
                            service.getStorageType(), e.getMessage());
                }
            }
        }
    }

    private CompletableFuture<Void> processImage(Path imagePath, Path outputDir,
                                                 AdvertisementRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path outputPath = outputDir.resolve("advertisement_" + request.getStartSegment() + ".ts");
                ffmpegService.convertImageToVideo(imagePath, outputPath, request.getDuration());
            } catch (Exception e) {
                log.error("Failed to process image advertisement", e);
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<Void> processVideo(Path videoPath, Path outputDir,
                                                 AdvertisementRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                ffmpegService.convertVideoToSegments(videoPath, outputDir,
                        request.getStartSegment(), request.getDuration());
            } catch (Exception e) {
                log.error("Failed to process video advertisement", e);
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<Void> processTs(Path tsPath, Path outputDir,
                                              AdvertisementRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path targetPath = outputDir.resolve("advertisement_" + request.getStartSegment() + ".ts");
                Files.copy(tsPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                log.error("Failed to process TS advertisement", e);
                throw new RuntimeException(e);
            }
        });
    }

    private void registerAdvertisement(String streamId, int segmentNumber, String path) {
        m3u8Service.registerAdvertisement(streamId,segmentNumber,path);
        streamAdvertisements.computeIfAbsent(streamId, k -> new ConcurrentHashMap<>())
                .put(segmentNumber, path);
        //m3u8Service.updatePlaylist(streamId);
    }

    public void removeAdvertisement(String streamId, int startSegment, int endSegment) {
        ConcurrentHashMap<Integer, String> streamAds = streamAdvertisements.get(streamId);
        if (streamAds != null) {
            List<StorageService> services = storageManager.getStoragesForStream(streamId);

            for (int i = startSegment; i <= endSegment; i++) {
                String path = streamAds.remove(i);
                if (path != null) {
                    // Remove from all storages
                    for (StorageService service : services) {
                        try {
                            String adSegmentName = "advertisement_" + i + ".ts";
                            service.deleteSegment(streamId, adSegmentName);
                        } catch (Exception e) {
                            log.error("Failed to delete advertisement from storage {}: {}",
                                    service.getStorageType(), e.getMessage());
                        }
                    }

                    // Remove local file
                    try {
                        Files.deleteIfExists(Path.of(path));
                    } catch (Exception e) {
                        log.error("Failed to delete local advertisement file", e);
                    }
                }
            }
            //remove from m3u8service
            //m3u8Service.updatePlaylist(streamId);
        }
    }

    private String getExtension(String filename) {
        return filename != null && filename.contains(".") ?
                filename.substring(filename.lastIndexOf(".")) : "";
    }
}
