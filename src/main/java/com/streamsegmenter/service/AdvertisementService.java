package com.streamsegmenter.service;

import com.streamsegmenter.config.StorageConfig;
import com.streamsegmenter.model.AdvertisementRequest;
import com.streamsegmenter.model.ScheduledStream;
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
    private final StreamService streamService;
    private final StreamSchedulerService streamSchedulerService;
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> streamAdvertisements = new ConcurrentHashMap<>();
    private static final String ADVERTISEMENT_PREFIX = "advertisement";

    public String insertAdvertisement(AdvertisementRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            if(!(checkStreamActive(request.getStreamId()) || checkStreamScheduled(request.getStreamId()))) {
                return "Advertisement didn't insert, because there is no scheduled or active stream with this id.";
            }

            Path tempFile = Files.createTempFile("ad-", getExtension(request.getFile().getOriginalFilename()));
            request.getFile().transferTo(tempFile.toFile());

            Path outputDir = config.resolvePath("streams", request.getStreamId());
            Files.createDirectories(outputDir);

            CompletableFuture<Void> processingFuture;
            Path outputPath = outputDir.resolve(ADVERTISEMENT_PREFIX + "_" + request.getStartSegment() + ".ts");

            switch (request.getType()) {
                case IMAGE:
                    processingFuture = processImage(tempFile, outputPath, request);
                    break;
                case VIDEO:
                    processingFuture = processVideo(tempFile, outputPath, request);
                    break;
                case TS_FILE:
                    processingFuture = processTs(tempFile, outputPath, request);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported advertisement type");
            }

            processingFuture.thenRun(() -> {
                try {
                    uploadAdvertisementToStorages(request.getStreamId(), outputDir, request.getStartSegment());
                    registerAdvertisement(request.getStreamId(), request.getStartSegment(), outputDir.toString(), request.getDuration());
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
        return "Advertisement inserted successfully";
    }

    private boolean checkStreamActive(String streamId) {
        return streamService.activeStreams.get(streamId) != null;
    }

    private boolean checkStreamScheduled(String streamId) {
        ScheduledStream stream = streamSchedulerService.getScheduledStreamsById(streamId);
        if(stream != null){
            storageManager.registerStreamStorages(streamId, stream.getStorageTypes());
            return true;
        }
        return false;
    }

    private void uploadAdvertisementToStorages(String streamId, Path adDir, int segmentNumber) {
        List<StorageService> services = storageManager.getStoragesForStream(streamId);

        // Birden fazla segment olabilir
        File[] adSegments = adDir.toFile().listFiles((dir, name) ->
                name.startsWith(ADVERTISEMENT_PREFIX + "_" + segmentNumber) && name.endsWith(".ts"));

        if (adSegments == null || adSegments.length == 0) {
            log.warn("No advertisement segments found in: {}", adDir);
            return;
        }

        for (File segment : adSegments) {
            for (StorageService service : services) {
                if (!(service instanceof LocalStorageService)) {
                    try {
                        service.uploadSegment(segment.toPath(), streamId).get();
                        log.info("Advertisement segment {} uploaded to storage: {}",
                                segment.getName(), service.getStorageType());
                    } catch (Exception e) {
                        log.error("Failed to upload advertisement to storage {}: {}",
                                service.getStorageType(), e.getMessage());
                    }
                }
            }
        }
    }

    private CompletableFuture<Void> processImage(Path imagePath, Path outputPath,
                                                 AdvertisementRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                ffmpegService.convertImageToVideo(imagePath, outputPath, request.getDuration(), request.getStartSegment());
            } catch (Exception e) {
                log.error("Failed to process image advertisement", e);
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<Void> processVideo(Path videoPath, Path outputPath,
                                                 AdvertisementRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                ffmpegService.convertVideoToSegments(videoPath, outputPath,
                        request.getStartSegment(), request.getDuration());
            } catch (Exception e) {
                log.error("Failed to process video advertisement", e);
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<Void> processTs(Path tsPath, Path outputPath,
                                              AdvertisementRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.copy(tsPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                log.error("Failed to process TS advertisement", e);
                throw new RuntimeException(e);
            }
        });
    }

    private void registerAdvertisement(String streamId, int segmentNumber, String path, int duration) {
        m3u8Service.registerAdvertisement(streamId, segmentNumber, path, duration);
        streamAdvertisements.computeIfAbsent(streamId, k -> new ConcurrentHashMap<>())
                .put(segmentNumber, path);
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
                            String adSegmentName = ADVERTISEMENT_PREFIX + "_" + i + ".ts";
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
        }
    }

    private String getExtension(String filename) {
        return filename != null && filename.contains(".") ?
                filename.substring(filename.lastIndexOf(".")) : "";
    }
}
