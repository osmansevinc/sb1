package com.streamsegmenter.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
public class AdvertisementRequest {
    private MultipartFile file;
    private String streamId;
    private Integer startSegment;
    private Integer duration; // Saniye cinsinden
    private Type type;

    public enum Type {
        IMAGE,
        VIDEO,
        TS_FILE
    }
}
