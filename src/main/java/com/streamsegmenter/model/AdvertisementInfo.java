package com.streamsegmenter.model;

import lombok.Data;

@Data
public class AdvertisementInfo {
    private final String path;
    private final int duration;
    private final String segmentName;
    private final boolean processed;
}
