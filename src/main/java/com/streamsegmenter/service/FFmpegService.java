package com.streamsegmenter.service;

import org.springframework.stereotype.Service;
import com.streamsegmenter.model.VideoQuality;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FFmpegService {
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final String ffmpegPath;

    public FFmpegService() {
        this.ffmpegPath = System.getProperty("os.name").toLowerCase().contains("win")
                ? "C:\\ffmpeg-master-latest-win64-gpl\\bin\\ffmpeg"
                : "ffmpeg";
    }

    public CompletableFuture<Void> startStreamProcessing(String streamId, String streamUrl,
                                                         Path outputPattern, VideoQuality quality) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<String> command = new ArrayList<>();
                command.add(ffmpegPath);
                command.add("-i");
                command.add(streamUrl);
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
                command.add("10");
                command.add("-segment_format");
                command.add("mpegts");
                command.add("-segment_list_size");
                command.add("0");
                command.add("-segment_list_flags");
                command.add("+live");
                command.add("-map");
                command.add("0");
                command.add(outputPattern.toString());

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.inheritIO();
                Process process = pb.start();
                activeProcesses.put(streamId, process);

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("FFmpeg process failed with exit code: " + exitCode);
                }
            } catch (Exception e) {
                log.error("Error in FFmpeg processing: {}", e.getMessage());
                throw new RuntimeException("Failed to process stream", e);
            } finally {
                activeProcesses.remove(streamId);
            }
        });
    }

    public void stopProcess(String streamId) {
        Process process = activeProcesses.remove(streamId);
        if (process != null && process.isAlive()) {
            try {
                process.destroy();
                // Force kill if not terminated within 5 seconds
                if (process.isAlive()) {
                    Thread.sleep(5000);
                    process.destroyForcibly();
                }
            } catch (Exception e) {
                log.error("Error stopping FFmpeg process: {}", e.getMessage());
            }
        }
    }
}
