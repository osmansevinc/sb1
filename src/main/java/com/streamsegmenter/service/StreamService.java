package com.streamsegmenter.service;

import com.streamsegmenter.config.StorageConfig;
import com.streamsegmenter.model.StreamContext;
import com.streamsegmenter.model.VideoQuality;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.LocalDateTime;
import java.time.Duration;

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

    public CompletableFuture<List<String>> startStream(String streamUrl, List<String> storageTypes,
                                                       VideoQuality quality, LocalDateTime startTime) {
        String streamId = UUID.randomUUID().toString();
        StreamContext context = new StreamContext(streamUrl);
        activeStreams.put(streamId, context);

        storageManager.registerStreamStorages(streamId, storageTypes);
        CompletableFuture<List<String>> resultFuture = new CompletableFuture<>();
        CompletableFuture<Void> readySignal = new CompletableFuture<>();

        if (startTime != null && startTime.isAfter(LocalDateTime.now())) {
            long delay = Duration.between(LocalDateTime.now(), startTime).toMillis();
            scheduler.schedule(() -> processStream(streamId, streamUrl, readySignal, quality),
                    delay, TimeUnit.MILLISECONDS);
        } else {
            processStream(streamId, streamUrl, readySignal, quality);
        }

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

            // Start monitoring for new segments
            ScheduledExecutorService segmentMonitor = Executors.newSingleThreadScheduledExecutor();
            segmentMonitor.scheduleAtFixedRate(() -> {
                if (!context.isActive()) {
                    segmentMonitor.shutdown();
                    return;
                }

                try {
                    Files.list(tempDir)
                            .filter(p -> p.getFileName().toString().endsWith(".ts"))
                            .forEach(segmentPath -> {
                                String segmentName = segmentPath.getFileName().toString();
                                processSegment(streamId, segmentPath, segmentName, isFirstSegmentCreated, readySignal);
                            });
                } catch (Exception e) {
                    log.error("Error monitoring segments: {}", e.getMessage());
                }
            }, 0, 5, TimeUnit.SECONDS);

            ffmpegFuture.whenComplete((v, ex) -> {
                if (ex != null) {
                    log.error("FFmpeg processing failed: {}", ex.getMessage());
                    stopStream(streamId);
                }
                segmentMonitor.shutdown();
            });

        } catch (Exception e) {
            log.error("Error in stream processing: {}", e.getMessage());
            if (!isFirstSegmentCreated.get()) {
                readySignal.completeExceptionally(e);
            }
            stopStream(streamId);
        }
    }

    private void processSegment(String streamId, Path segmentPath, String segmentName,
                                AtomicBoolean isFirstSegmentCreated, CompletableFuture<Void> readySignal) {
        try {
            List<CompletableFuture<String>> uploads = new ArrayList<>();
            List<StorageService> services = storageManager.getStoragesForStream(streamId);

            for (StorageService service : services) {
                uploads.add(service.uploadSegment(segmentPath, streamId));
            }

            CompletableFuture.allOf(uploads.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        m3u8Service.addSegment(streamId, segmentName);
                        if (isFirstSegmentCreated.compareAndSet(false, true)) {
                            readySignal.complete(null);
                        }
                    })
                    .exceptionally(e -> {
                        log.error("Error processing segment: {}", e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error processing segment: {}", e.getMessage());
        }
    }

    public void stopStream(String streamId) {
        StreamContext context = activeStreams.remove(streamId);
        if (context != null) {
            context.setActive(false);
            ffmpegService.stopProcess(streamId);
            m3u8Service.clearStreamCache(streamId);
            cleanupStreamDirectory(streamId);
            storageManager.removeStreamStorages(streamId);
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
