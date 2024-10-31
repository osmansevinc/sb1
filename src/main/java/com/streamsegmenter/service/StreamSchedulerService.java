package com.streamsegmenter.service;

import com.streamsegmenter.model.ScheduledStream;
import com.streamsegmenter.model.StreamUpdateRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamSchedulerService {
    private final RedisTemplate<String, ScheduledStream> redisTemplate;
    private final StreamService streamService;
    private final M3u8Service m3u8Service;
    private static final String SCHEDULED_STREAMS_KEY = "scheduled_streams";
    private final String instanceId = generateInstanceId();

    private String generateInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString();
        } catch (Exception e) {
            return "instance-" + UUID.randomUUID().toString();
        }
    }

    public Map<String, Object> getActiveAndScheduledStreams() {
        Map<String, Object> result = new HashMap<>();

        // Aktif streamler
        Map<String, List<String>> activeStreamUrls = new HashMap<>();
        streamService.activeStreams.forEach((id, context) -> {
            activeStreamUrls.put(id, m3u8Service.getM3u8Urls(id));
        });
        result.put("active", activeStreamUrls);

        // Planlanmış streamler
        result.put("scheduled", this.getAllScheduledStreams());

        return result;
    }


    public void scheduleStream(ScheduledStream stream) {
        if (stream.getId() == null) {
            stream.setId(UUID.randomUUID().toString());
        }
        stream.setProcessed(false);
        stream.setProcessingInstance(null);
        redisTemplate.opsForHash().put(SCHEDULED_STREAMS_KEY, stream.getId(), stream);
        log.info("Stream scheduled: {}", stream.getId());
    }

    public List<ScheduledStream> getAllScheduledStreams() {
        List<ScheduledStream> streams = new ArrayList<>();
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(SCHEDULED_STREAMS_KEY);
        entries.values().forEach(obj -> streams.add((ScheduledStream) obj));
        return streams;
    }

    public boolean updateScheduledStream(String streamId, StreamUpdateRequest updateRequest) {
        ScheduledStream stream = (ScheduledStream) redisTemplate.opsForHash()
                .get(SCHEDULED_STREAMS_KEY, streamId);

        if (stream == null || stream.isProcessed()) {
            return false;
        }

        if (updateRequest.getStreamUrl() != null) {
            stream.setStreamUrl(updateRequest.getStreamUrl());
        }
        if (updateRequest.getVideoQuality() != null) {
            stream.setVideoQuality(updateRequest.getVideoQuality());
        }
        if (updateRequest.getStartTime() != null) {
            stream.setStartTime(updateRequest.getStartTime());
        }

        redisTemplate.opsForHash().put(SCHEDULED_STREAMS_KEY, streamId, stream);
        log.info("Stream updated: {}", streamId);
        return true;
    }

    public boolean cancelScheduledStream(String streamId) {
        ScheduledStream stream = (ScheduledStream) redisTemplate.opsForHash()
                .get(SCHEDULED_STREAMS_KEY, streamId);

        if (stream == null || stream.isProcessed()) {
            return false;
        }

        redisTemplate.opsForHash().delete(SCHEDULED_STREAMS_KEY, streamId);
        log.info("Stream cancelled: {}", streamId);
        return true;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void processScheduledStreams() {
        LocalDateTime now = LocalDateTime.now();

        redisTemplate.opsForHash()
                .entries(SCHEDULED_STREAMS_KEY)
                .forEach((id, streamObj) -> {
                    ScheduledStream stream = (ScheduledStream) streamObj;

                    if (!stream.isProcessed() &&
                            stream.getProcessingInstance() == null &&
                            stream.getStartTime().isBefore(now)) {

                        stream.setProcessingInstance(instanceId);
                        redisTemplate.opsForHash().put(SCHEDULED_STREAMS_KEY, id, stream);

                        try {
                            // Stream ID'sini scheduled stream ID'si olarak kullan
                            streamService.startStream(
                                    stream.getStreamUrl(),
                                    stream.getStorageTypes(),
                                    stream.getVideoQuality(),
                                    null,
                                    stream.getWatermark(),
                                    stream.getId() // Stream ID'yi geçir
                            );
                            stream.setProcessed(true);
                            redisTemplate.opsForHash().put(SCHEDULED_STREAMS_KEY, id, stream);
                            log.info("Scheduled stream started: {}", stream.getId());
                        } catch (Exception e) {
                            log.error("Failed to start scheduled stream {}: {}", id, e.getMessage());
                            stream.setProcessingInstance(null);
                            redisTemplate.opsForHash().put(SCHEDULED_STREAMS_KEY, id, stream);
                        }
                    }
                });
    }

    public void removeScheduledStream(String streamId) {
        redisTemplate.opsForHash().delete(SCHEDULED_STREAMS_KEY, streamId);
        log.info("Stream removed from scheduler: {}", streamId);
    }
}
