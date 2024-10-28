package com.streamsegmenter.model;

import lombok.Data;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class StreamContext {
    private final String streamUrl;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicInteger sequenceNumber = new AtomicInteger(0);
    
    public StreamContext(String streamUrl) {
        this.streamUrl = streamUrl;
    }
    
    public boolean isActive() {
        return active.get();
    }
    
    public void setActive(boolean value) {
        active.set(value);
    }
    
    public int getNextSequence() {
        return sequenceNumber.getAndIncrement();
    }
}