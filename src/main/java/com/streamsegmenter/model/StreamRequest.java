package com.streamsegmenter.model;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
public class StreamRequest {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @NotBlank(message = "Stream URL is required")
    @Pattern(regexp = "^(rtmp|rtsp|http|https)://.*", message = "Invalid stream URL format")
    private String streamUrl;

    private List<String> storageTypes;

    private VideoQuality videoQuality = VideoQuality.LOW;

    private String startTimeStr;

    public LocalDateTime getStartTime() {
        return startTimeStr != null ? LocalDateTime.parse(startTimeStr, DATE_FORMATTER) : null;
    }

    public void setStartTime(String startTimeStr) {
        this.startTimeStr = startTimeStr;
    }
}
