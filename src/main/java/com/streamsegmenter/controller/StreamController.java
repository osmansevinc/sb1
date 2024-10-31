package com.streamsegmenter.controller;

import com.streamsegmenter.model.ScheduledStream;
import com.streamsegmenter.model.StreamRequest;
import com.streamsegmenter.model.StreamUpdateRequest;
import com.streamsegmenter.service.StreamSchedulerService;
import com.streamsegmenter.service.StreamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
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
                    null,
                    request.getWatermark(),
                    null
            ).get(30, TimeUnit.SECONDS);

            return ResponseEntity.ok().body(urls);
        } catch (Exception e) {
            log.error("Failed to start stream", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to start stream: " + e.getMessage());
        }
    }

    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveAndScheduledStreams() {
        try {
            Map<String, Object> streams = schedulerService.getActiveAndScheduledStreams();
            return ResponseEntity.ok(streams);
        } catch (Exception e) {
            log.error("Failed to get active and scheduled streams", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/scheduled/{streamId}")
    public ResponseEntity<String> updateScheduledStream(
            @PathVariable String streamId,
            @RequestBody StreamUpdateRequest updateRequest) {
        try {
            boolean updated = schedulerService.updateScheduledStream(streamId, updateRequest);
            if (updated) {
                return ResponseEntity.ok("Stream updated successfully");
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to update scheduled stream", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to update stream: " + e.getMessage());
        }
    }

    @DeleteMapping("/scheduled/{streamId}")
    public ResponseEntity<String> cancelScheduledStream(@PathVariable String streamId) {
        try {
            boolean cancelled = schedulerService.cancelScheduledStream(streamId);
            if (cancelled) {
                return ResponseEntity.ok("Stream cancelled successfully");
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to cancel scheduled stream", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to cancel stream: " + e.getMessage());
        }
    }

    @PostMapping("/stop/{streamId}")
    public ResponseEntity<String> stopStream(@PathVariable String streamId) {
        try {
            streamService.stopStream(streamId);
            schedulerService.removeScheduledStream(streamId);
            return ResponseEntity.ok("Stream stopped successfully");
        } catch (Exception e) {
            log.error("Failed to stop stream", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to stop stream: " + e.getMessage());
        }
    }
}
