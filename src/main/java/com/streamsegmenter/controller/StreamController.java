package com.streamsegmenter.controller;

import com.streamsegmenter.model.StreamRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import com.streamsegmenter.service.StreamService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class StreamController {
    private final StreamService streamService;

    @PostMapping("/start")
    public ResponseEntity<CompletableFuture<List<String>>> startStream(@RequestBody StreamRequest request) {
        CompletableFuture<List<String>> m3u8Urls = streamService.startStream(
                request.getStreamUrl(),
                request.getStorageTypes(),
                request.getVideoQuality(),
                request.getStartTime()
        );
        return ResponseEntity.ok().body(m3u8Urls);
    }

    @PostMapping("/stop/{streamId}")
    public void stopStream(@PathVariable String streamId) {
        streamService.stopStream(streamId);
    }
}