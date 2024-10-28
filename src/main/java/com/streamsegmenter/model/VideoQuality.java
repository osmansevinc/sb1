package com.streamsegmenter.model;

import lombok.Getter;

@Getter
public enum VideoQuality {
    LOW(480, 360, 800, 96),
    MEDIUM(1280, 720, 2500, 128),
    HIGH(1920, 1080, 5000, 192);

    private final int width;
    private final int height;
    private final long videoBitrateKbps;
    private final long audioBitrateKbps;

    VideoQuality(int width, int height, long videoBitrateKbps, long audioBitrateKbps) {
        this.width = width;
        this.height = height;
        this.videoBitrateKbps = videoBitrateKbps;
        this.audioBitrateKbps = audioBitrateKbps;
    }

    public String getResolution() {
        return width + "x" + height;
    }

    public long getVideoBitrateInKbps() {
        return videoBitrateKbps;
    }

    public long getAudioBitrateInKbps() {
        return audioBitrateKbps;
    }
}
