package com.streamsegmenter.controller;

import com.streamsegmenter.model.ScheduledStream;
import com.streamsegmenter.model.StreamRequest;
import com.streamsegmenter.service.StreamSchedulerService;
import com.streamsegmenter.service.StreamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class StreamController {
    private final StreamService streamService;
    private final StreamSchedulerService schedulerService;

    @PostMapping("/start")
    public ResponseEntity<?> startStream(@RequestBody StreamRequest request) {
        if (request.getStartTime() != null) {
            ScheduledStream scheduledStream = new ScheduledStream();
            scheduledStream.setStreamUrl(request.getStreamUrl());
            scheduledStream.setStorageTypes(request.getStorageTypes());
            scheduledStream.setVideoQuality(request.getVideoQuality());
            scheduledStream.setStartTime(request.getStartTime());

            schedulerService.scheduleStream(scheduledStream);
            return ResponseEntity.accepted().body("Stream scheduled for " + request.getStartTime());
        }

        try {
            List<String> urls = streamService.startStream(
                    request.getStreamUrl(),
                    request.getStorageTypes(),
                    request.getVideoQuality(),
                    null
            ).get(30, TimeUnit.SECONDS);

            return ResponseEntity.ok().body(urls);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to start stream: " + e.getMessage());
        }
    }

    @PostMapping("/stop/{streamId}")
    public ResponseEntity<String> stopStream(@PathVariable String streamId) {
        try {
            streamService.stopStream(streamId);
            schedulerService.removeScheduledStream(streamId);
            return ResponseEntity.ok("Stream stopped successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to stop stream: " + e.getMessage());
        }
    }
}
