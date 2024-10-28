package com.streamsegmenter.controller;

import com.streamsegmenter.service.M3u8Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class StreamContentController {
    private final M3u8Service m3u8Service;

    @GetMapping("/{streamId}/{storageType}/playlist.m3u8")
    public ResponseEntity<String> getPlaylist(
            @PathVariable String streamId,
            @PathVariable String storageType) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.apple.mpegurl")
                .header("Access-Control-Allow-Origin", "*")
                .body(m3u8Service.getPlaylistContent(streamId, storageType));
    }
}
