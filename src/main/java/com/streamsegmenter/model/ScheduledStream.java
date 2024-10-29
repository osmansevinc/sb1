package com.streamsegmenter.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ScheduledStream {
    private String id;
    private String streamUrl;
    private List<String> storageTypes;
    private VideoQuality videoQuality;
    private LocalDateTime startTime;
    private boolean processed;
    private String processingInstance;
}
