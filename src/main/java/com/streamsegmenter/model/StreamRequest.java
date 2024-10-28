package com.streamsegmenter.model;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.util.List;
import java.time.LocalDateTime;

@Data
public class StreamRequest {
    @NotBlank(message = "Stream URL is required")
    @Pattern(regexp = "^(rtmp|rtsp|http|https)://.*", message = "Invalid stream URL format")
    private String streamUrl;
    
    private List<String> storageTypes;
    
    private VideoQuality videoQuality = VideoQuality.MEDIUM;

    private LocalDateTime startTime; // Null means start immediately
}