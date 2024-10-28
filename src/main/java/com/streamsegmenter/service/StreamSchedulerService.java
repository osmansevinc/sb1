package com.streamsegmenter.service;

import com.streamsegmenter.model.ScheduledStream;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamSchedulerService {
    private final RedisTemplate<String, ScheduledStream> redisTemplate;
    private final StreamService streamService;
    private static final String SCHEDULED_STREAMS_KEY = "scheduled_streams";

    public void scheduleStream(ScheduledStream stream) {
        redisTemplate.opsForHash().put(SCHEDULED_STREAMS_KEY, stream.getId(), stream);
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void processScheduledStreams() {
        LocalDateTime now = LocalDateTime.now();

        redisTemplate.opsForHash()
                .entries(SCHEDULED_STREAMS_KEY)
                .forEach((id, streamObj) -> {
                    ScheduledStream stream = (ScheduledStream) streamObj;
                    if (!stream.isProcessed() && stream.getStartTime().isBefore(now)) {
                        try {
                            streamService.startStream(
                                    stream.getStreamUrl(),
                                    stream.getStorageTypes(),
                                    stream.getVideoQuality(),
                                    null
                            );
                            stream.setProcessed(true);
                            redisTemplate.opsForHash().put(SCHEDULED_STREAMS_KEY, id, stream);
                        } catch (Exception e) {
                            log.error("Failed to start scheduled stream {}: {}", id, e.getMessage());
                        }
                    }
                });
    }
}
