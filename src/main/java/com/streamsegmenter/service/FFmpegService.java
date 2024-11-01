package com.streamsegmenter.service;

import com.streamsegmenter.model.StreamRequest;
import org.springframework.stereotype.Service;
import com.streamsegmenter.model.VideoQuality;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@Service
public class FFmpegService {
    private static final Logger performanceLogger = LoggerFactory.getLogger("com.streamsegmenter.performance");
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final String ffmpegPath;
    private static final int STANDARD_SEGMENT_DURATION = 5;

    public FFmpegService() {
        this.ffmpegPath = System.getProperty("os.name").toLowerCase().contains("win")
                ? "C:\\ffmpeg-master-latest-win64-gpl\\bin\\ffmpeg"
                : "ffmpeg";
    }

    public CompletableFuture<Void> startStreamProcessing(String streamId, String streamUrl,
                                                         Path outputPattern, VideoQuality quality,
                                                         StreamRequest.Watermark watermark) {
        long startTime = System.currentTimeMillis();
        return CompletableFuture.runAsync(() -> {
            try {
                List<String> command = new ArrayList<>();
                command.add(ffmpegPath);
                command.add("-i");
                command.add(streamUrl);

                if (watermark != null) {
                    if (watermark.getImagePath() != null) {
                        command.add("-i");
                        command.add(watermark.getImagePath());
                        command.add("-filter_complex");
                        command.add(String.format(
                                "[1:v]scale=-1:%d,format=rgba,colorchannelmixer=aa=%f[watermark];" +
                                        "[0:v][watermark]overlay=%d:%d",
                                watermark.getSize(), watermark.getOpacity(),
                                watermark.getX(), watermark.getY()
                        ));
                    } else if (watermark.getText() != null) {
                        command.add("-vf");
                        command.add(String.format(
                                "drawtext=text='%s':fontsize=%d:fontcolor=%s@%f:x=%d:y=%d",
                                watermark.getText(), watermark.getSize(), watermark.getColor(),
                                watermark.getOpacity(), watermark.getX(), watermark.getY()
                        ));
                    }
                }

                command.add("-c:v");
                command.add("libx264");
                command.add("-b:v");
                command.add(quality.getVideoBitrateKbps() + "k");

                command.add("-c:a");
                command.add("aac");
                command.add("-b:a");
                command.add(quality.getAudioBitrateKbps() + "k");

                command.add("-f");
                command.add("segment");
                command.add("-segment_time");
                command.add(String.valueOf(STANDARD_SEGMENT_DURATION));
                command.add("-segment_format");
                command.add("mpegts");
                command.add("-segment_list_size");
                command.add("0");
                command.add("-segment_list_flags");
                command.add("+live");
                command.add("-map");
                command.add("0");
                command.add(outputPattern.toString());

                log.debug("Starting FFmpeg process with command: {}", String.join(" ", command));
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.inheritIO();
                Process process = pb.start();
                activeProcesses.put(streamId, process);

                int exitCode = process.waitFor();
                long duration = System.currentTimeMillis() - startTime;
                performanceLogger.info("FFmpeg process completed in {} ms for streamId: {}", duration, streamId);

                if (exitCode != 0) {
                    throw new RuntimeException("FFmpeg process failed with exit code: " + exitCode);
                }
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                performanceLogger.error("FFmpeg process failed in {} ms for streamId: {}", duration, streamId);
                log.error("Error in FFmpeg processing: {}", e.getMessage());
                throw new RuntimeException("Failed to process stream", e);
            } finally {
                activeProcesses.remove(streamId);
            }
        });
    }

    public void convertImageToVideo(Path imagePath, Path outputPath, int durationSeconds, int startSegment) {
        long startTime = System.currentTimeMillis();
        try {
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-loop");
            command.add("1");
            command.add("-i");
            command.add(imagePath.toString());
            command.add("-vf");
            command.add("scale=trunc(iw/2)*2:trunc(ih/2)*2");
            command.add("-t");
            command.add(String.valueOf(durationSeconds));
            command.add("-pix_fmt");
            command.add("yuv420p");

            // Eğer süre 5 saniyeden büyükse segmentlere böl
            if (durationSeconds > STANDARD_SEGMENT_DURATION) {
                command.add("-f");
                command.add("segment");
                command.add("-segment_time");
                command.add(String.valueOf(STANDARD_SEGMENT_DURATION));
                command.add("-segment_format");
                command.add("mpegts");
                command.add(outputPath.getParent().resolve(String.format("advertisement_%d_%%d.ts", startSegment)).toString());
            } else {
                command.add("-f");
                command.add("mpegts");
                command.add(outputPath.toString());
            }

            log.debug("Starting image conversion with command: {}", String.join(" ", command));
            Process process = new ProcessBuilder(command)
                    .inheritIO()
                    .start();

            int exitCode = process.waitFor();
            long duration = System.currentTimeMillis() - startTime;

            if (exitCode == 0) {
                performanceLogger.info("Image conversion completed in {} ms", duration);
            } else {
                throw new RuntimeException("FFmpeg image conversion failed with exit code: " + exitCode);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceLogger.error("Image conversion failed in {} ms: {}", duration, e.getMessage());
            throw new RuntimeException("Failed to convert image to video", e);
        }
    }

    public void convertVideoToSegments(Path videoPath, Path outputPath, int startSegment, int durationSeconds) {
        long startTime = System.currentTimeMillis();
        try {
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-i");
            command.add(videoPath.toString());

            // Video kodek ayarları
            command.add("-c:v");
            command.add("libx264");
            command.add("-c:a");
            command.add("aac");

            if (durationSeconds > 0) {
                command.add("-t");
                command.add(String.valueOf(durationSeconds));
            }

            // Eğer süre 5 saniyeden büyükse segmentlere böl
            if (durationSeconds > STANDARD_SEGMENT_DURATION) {
                command.add("-f");
                command.add("segment");
                command.add("-segment_time");
                command.add(String.valueOf(STANDARD_SEGMENT_DURATION));
                command.add("-segment_format");
                command.add("mpegts");
                command.add("-segment_list_size");
                command.add("0");
                command.add(outputPath.getParent().resolve(String.format("advertisement_%d_%%d.ts", startSegment)).toString());
            } else {
                command.add("-f");
                command.add("mpegts");
                command.add(outputPath.toString());
            }

            log.debug("Starting video conversion with command: {}", String.join(" ", command));
            Process process = new ProcessBuilder(command)
                    .inheritIO()
                    .start();

            int exitCode = process.waitFor();
            long duration = System.currentTimeMillis() - startTime;

            if (exitCode == 0) {
                performanceLogger.info("Video conversion completed in {} ms", duration);
            } else {
                throw new RuntimeException("FFmpeg video segmentation failed with exit code: " + exitCode);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceLogger.error("Video segmentation failed in {} ms: {}", duration, e.getMessage());
            throw new RuntimeException("Failed to convert video to segments", e);
        }
    }

    public void stopProcess(String streamId) {
        Process process = activeProcesses.remove(streamId);
        if (process != null && process.isAlive()) {
            try {
                process.destroy();
                if (process.isAlive()) {
                    Thread.sleep(5000);
                    process.destroyForcibly();
                }
                log.info("FFmpeg process stopped for streamId: {}", streamId);
            } catch (Exception e) {
                log.error("Error stopping FFmpeg process: {}", e.getMessage());
            }
        }
    }
}
