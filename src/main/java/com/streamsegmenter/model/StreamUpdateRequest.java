package com.streamsegmenter.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StreamUpdateRequest {
    private String streamUrl;
    private VideoQuality videoQuality;
    private String startTimeStr;

    public LocalDateTime getStartTime() {
        return startTimeStr != null ? LocalDateTime.parse(startTimeStr,
                StreamRequest.DATE_FORMATTER) : null;
    }
}
