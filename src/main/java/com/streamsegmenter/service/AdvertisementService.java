package com.streamsegmenter.service;

import com.streamsegmenter.config.StorageConfig;
import com.streamsegmenter.model.AdvertisementRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvertisementService {
    private final StorageConfig config;
    private final FFmpegService ffmpegService;
    private final M3u8Service m3u8Service;
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
                registerAdvertisement(request.getStreamId(), request.getStartSegment(),
                        outputDir.toString());
                long duration = System.currentTimeMillis() - startTime;
                log.info("Advertisement processing completed in {} ms", duration);
            }).exceptionally(e -> {
                log.error("Failed to process advertisement", e);
                return null;
            });

        } catch (Exception e) {
            log.error("Failed to insert advertisement", e);
            throw new RuntimeException("Advertisement insertion failed", e);
        }
    }

    private CompletableFuture<Void> processImage(Path imagePath, Path outputDir,
                                                 AdvertisementRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Resmi video segmentine çevir
                Path outputPath = outputDir.resolve("segment_" + request.getStartSegment() + ".ts");
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
                // Videoyu TS segmentlerine çevir
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
                // TS dosyasını doğrudan kopyala
                Path targetPath = outputDir.resolve("segment_" + request.getStartSegment() + ".ts");
                Files.copy(tsPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                log.error("Failed to process TS advertisement", e);
                throw new RuntimeException(e);
            }
        });
    }

    private void registerAdvertisement(String streamId, int segmentNumber, String path) {
        streamAdvertisements.computeIfAbsent(streamId, k -> new ConcurrentHashMap<>())
                .put(segmentNumber, path);
        m3u8Service.updatePlaylist(streamId);
    }

    public void removeAdvertisement(String streamId, int startSegment, int endSegment) {
        ConcurrentHashMap<Integer, String> streamAds = streamAdvertisements.get(streamId);
        if (streamAds != null) {
            for (int i = startSegment; i <= endSegment; i++) {
                String path = streamAds.remove(i);
                if (path != null) {
                    try {
                        Files.deleteIfExists(Path.of(path));
                    } catch (Exception e) {
                        log.error("Failed to delete advertisement file", e);
                    }
                }
            }
            m3u8Service.updatePlaylist(streamId);
        }
    }

    private String getExtension(String filename) {
        return filename != null && filename.contains(".") ?
                filename.substring(filename.lastIndexOf(".")) : "";
    }
}
