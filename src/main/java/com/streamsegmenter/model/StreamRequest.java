package com.streamsegmenter.model;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
public class StreamRequest {
    public static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @NotBlank(message = "Stream URL is required")
    @Pattern(regexp = "^(rtmp|rtsp|http|https)://.*", message = "Invalid stream URL format")
    private String streamUrl;

    private List<String> storageTypes;
    private VideoQuality videoQuality = VideoQuality.LOW;
    private String startTimeStr;
    private Watermark watermark;

    public LocalDateTime getStartTime() {
        return startTimeStr != null ? LocalDateTime.parse(startTimeStr, DATE_FORMATTER) : null;
    }

    public void setStartTime(String startTimeStr) {
        this.startTimeStr = startTimeStr;
    }

    @Data
    public static class Watermark {
        private String text;
        private String imagePath;
        private int x = 10; // Default position
        private int y = 10;
        private int size = 24; // Default font size
        private String color = "white"; // Default color
        private float opacity = 0.8f; // Default opacity
    }
}
