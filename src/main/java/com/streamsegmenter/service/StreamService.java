package com.streamsegmenter.service;

import com.streamsegmenter.config.StorageConfig;
import com.streamsegmenter.model.StreamContext;
import com.streamsegmenter.model.VideoQuality;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.*;
import java.util.concurrent.*;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {
    private final StorageConfig config;
    private final StorageManager storageManager;
    private final M3u8Service m3u8Service;
    private final FFmpegService ffmpegService;
    private final ConcurrentHashMap<String, StreamContext> activeStreams = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<String, Set<String>> processedSegments = new ConcurrentHashMap<>();
    private static final int SEGMENT_PROCESSING_DELAY_MS = 1000;

    public CompletableFuture<List<String>> startStream(String streamUrl, List<String> storageTypes,
                                                       VideoQuality quality, LocalDateTime startTime) {
        String streamId = UUID.randomUUID().toString();
        StreamContext context = new StreamContext(streamUrl);
        activeStreams.put(streamId, context);
        processedSegments.put(streamId, ConcurrentHashMap.newKeySet());

        storageManager.registerStreamStorages(streamId, storageTypes);
        CompletableFuture<List<String>> resultFuture = new CompletableFuture<>();
        CompletableFuture<Void> readySignal = new CompletableFuture<>();

        processStream(streamId, streamUrl, readySignal, quality);

        readySignal.orTimeout(30, TimeUnit.SECONDS)
                .thenApply(v -> m3u8Service.getM3u8Urls(streamId))
                .whenComplete((urls, ex) -> {
                    if (ex != null) {
                        stopStream(streamId);
                        resultFuture.completeExceptionally(
                                new RuntimeException("Failed to start stream within timeout", ex));
                    } else {
                        resultFuture.complete(urls);
                    }
                });

        return resultFuture;
    }

    @Async
    protected void processStream(String streamId, String streamUrl, CompletableFuture<Void> readySignal,
                                 VideoQuality quality) {
        StreamContext context = activeStreams.get(streamId);
        Path tempDir = config.resolvePath("streams", streamId);
        AtomicBoolean isFirstSegmentCreated = new AtomicBoolean(false);

        try {
            Files.createDirectories(tempDir);
            Path segmentPattern = tempDir.resolve("segment_%d.ts");

            CompletableFuture<Void> ffmpegFuture = ffmpegService.startStreamProcessing(
                    streamId, streamUrl, segmentPattern, quality);

            WatchService watchService = FileSystems.getDefault().newWatchService();
            tempDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

            //burda bir geriden gitmeliyiz
            CompletableFuture.runAsync(() -> {
                try {
                    while (context.isActive()) {
                        WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                        if (key != null) {
                            for (WatchEvent<?> event : key.pollEvents()) {
                                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                                    Path newPath = tempDir.resolve((Path) event.context());
                                    String segmentName = newPath.getFileName().toString();
                                    while(!Files.exists(tempDir.resolve(getNextSegment(segmentName) ))) {
                                        Thread.sleep(SEGMENT_PROCESSING_DELAY_MS);
                                    }
                                    if (segmentName.endsWith(".ts")) {
                                        // Wait for the file to be completely written
                                        processSegment(streamId, newPath, segmentName, isFirstSegmentCreated, readySignal);
                                    }
                                }
                            }
                            key.reset();
                        }
                    }
                } catch (Exception e) {
                    log.error("Error in segment monitoring: {}", e.getMessage());
                }
            });

            ffmpegFuture.whenComplete((v, ex) -> {
                if (ex != null) {
                    log.error("FFmpeg processing failed: {}", ex.getMessage());
                    stopStream(streamId);
                }
                try {
                    watchService.close();
                } catch (Exception e) {
                    log.error("Error closing watch service: {}", e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("Error in stream processing: {}", e.getMessage());
            if (!isFirstSegmentCreated.get()) {
                readySignal.completeExceptionally(e);
            }
            stopStream(streamId);
        }
    }

    private String getNextSegment (String path) {
        Pattern pattern = Pattern.compile("segment_(\\d+)\\.ts");
        Matcher matcher = pattern.matcher(path);

        if (matcher.find()) {
            int segmentNumber = Integer.parseInt(matcher.group(1)) + 4;
            String newSegment = "segment_" + segmentNumber + ".ts";
            return matcher.replaceFirst(newSegment);
        }
        return null;
    }

    private void processSegment(String streamId, Path segmentPath, String segmentName,
                                AtomicBoolean isFirstSegmentCreated, CompletableFuture<Void> readySignal) {
        Set<String> processed = processedSegments.get(streamId);
        if (processed != null && !processed.contains(segmentName)) {
            try {
                if (!Files.exists(segmentPath) || Files.size(segmentPath) == 0) {
                    log.warn("Skipping empty or non-existent segment: {}", segmentPath);
                    return;
                }

                List<CompletableFuture<String>> uploads = new ArrayList<>();
                List<StorageService> services = storageManager.getStoragesForStream(streamId);

                for (StorageService service : services) {
                    uploads.add(service.uploadSegment(segmentPath, streamId));
                }

                CompletableFuture.allOf(uploads.toArray(new CompletableFuture[0]))
                        .thenRun(() -> {
                            processed.add(segmentName);
                            m3u8Service.addSegment(streamId, segmentName);
                            if (isFirstSegmentCreated.compareAndSet(false, true)) {
                                readySignal.complete(null);
                            }
                            log.info("Successfully processed segment: {}", segmentName);
                        })
                        .exceptionally(e -> {
                            log.error("Error processing segment: {} - {}", segmentName, e.getMessage());
                            return null;
                        });
            } catch (Exception e) {
                log.error("Error processing segment: {} - {}", segmentName, e.getMessage());
            }
        }
    }

    public void stopStream(String streamId) {
        StreamContext context = activeStreams.remove(streamId);
        if (context != null) {
            context.setActive(false);
            ffmpegService.stopProcess(streamId);
            m3u8Service.clearStreamCache(streamId);

            // Clean up S3 first
            List<StorageService> services = storageManager.getStoragesForStream(streamId);
            for (StorageService service : services) {
                try {
                    service.deleteStream(streamId);
                } catch (Exception e) {
                    log.error("Error cleaning up storage for service {}: {}",
                            service.getClass().getSimpleName(), e.getMessage());
                }
            }

            // Then clean up local files
            cleanupStreamDirectory(streamId);
            storageManager.removeStreamStorages(streamId);
            processedSegments.remove(streamId);
        }
    }

    private void cleanupStreamDirectory(String streamId) {
        try {
            Path streamDir = config.resolvePath("streams", streamId);
            if (Files.exists(streamDir)) {
                Files.walk(streamDir)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (Exception e) {
                                log.warn("Failed to delete path: {}", path);
                            }
                        });
            }
        } catch (Exception e) {
            log.error("Error cleaning up stream directory: {}", e.getMessage());
        }
    }
}
