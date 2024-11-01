package com.streamsegmenter.controller;

import com.streamsegmenter.model.AdvertisementRequest;
import com.streamsegmenter.service.AdvertisementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/advertisement")
@RequiredArgsConstructor
public class AdvertisementController {
    private final AdvertisementService advertisementService;

    @PostMapping("/insert")
    public ResponseEntity<String> insertAdvertisement(
            @RequestParam("file") MultipartFile file,
            @RequestParam("streamId") String streamId,
            @RequestParam("startSegment") Integer startSegment,
            @RequestParam("duration") Integer duration,
            @RequestParam(value = "type", defaultValue = "VIDEO") String type) {

        try {
            AdvertisementRequest request = AdvertisementRequest.builder()
                    .file(file)
                    .streamId(streamId)
                    .startSegment(startSegment)
                    .duration(duration)
                    .type(AdvertisementRequest.Type.valueOf(type.toUpperCase()))
                    .build();

            String result = advertisementService.insertAdvertisement(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to insert advertisement", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to insert advertisement: " + e.getMessage());
        }
    }

    @DeleteMapping("/{streamId}/{segmentRange}")
    public ResponseEntity<String> removeAdvertisement(
            @PathVariable String streamId,
            @PathVariable String segmentRange) {
        try {
            String[] range = segmentRange.split("-");
            int startSegment = Integer.parseInt(range[0]);
            int endSegment = range.length > 1 ? Integer.parseInt(range[1]) : startSegment;

            advertisementService.removeAdvertisement(streamId, startSegment, endSegment);
            return ResponseEntity.ok("Advertisement removed successfully");
        } catch (Exception e) {
            log.error("Failed to remove advertisement", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to remove advertisement: " + e.getMessage());
        }
    }
}
