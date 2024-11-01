package com.streamsegmenter.service;

import com.streamsegmenter.model.AdvertisementInfo;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Path;
import java.nio.file.Files;

@Slf4j
@Service
@RequiredArgsConstructor
public class M3u8Service {
    private static final Logger performanceLogger = LoggerFactory.getLogger("com.streamsegmenter.performance");
    private final StorageManager storageManager;
    private final Map<String, TreeSet<Integer>> streamSequences = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> playlistContents = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, AdvertisementInfo>> advertisementSegments = new ConcurrentHashMap<>();

    private static final int SEGMENT_DURATION = 5;
    private static final int MAX_SEGMENTS = 6;

    @Cacheable(value = "m3u8Urls", key = "#streamId", unless = "#result == null")
    public List<String> getM3u8Urls(String streamId) {
        long startTime = System.currentTimeMillis();
        try {
            List<String> urls = new ArrayList<>();
            List<StorageService> services = storageManager.getStoragesForStream(streamId);

            for (StorageService service : services) {
                urls.add(String.format("/api/stream/%s/%s/playlist.m3u8",
                        streamId, service.getStorageType().toLowerCase()));
            }

            long duration = System.currentTimeMillis() - startTime;
            performanceLogger.info("M3u8 URLs retrieved in {} ms for streamId: {}", duration, streamId);
            return urls;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceLogger.error("Failed to get M3u8 URLs in {} ms for streamId: {}", duration, streamId);
            throw e;
        }
    }

    public void registerAdvertisement(String streamId, int segmentNumber, String segmentPath, int duration) {
        int totalSegments = (int) Math.ceil(duration / (double) SEGMENT_DURATION);
        Map<Integer, AdvertisementInfo> streamAds = advertisementSegments.computeIfAbsent(streamId, k -> new ConcurrentHashMap<>());

        if (duration <= SEGMENT_DURATION) {
            String adSegmentName = String.format("advertisement_%d.ts", segmentNumber);
            streamAds.put(segmentNumber, new AdvertisementInfo(segmentPath, duration, adSegmentName, false));
        } else {
            for (int i = 0; i < totalSegments; i++) {
                int currentSegment = segmentNumber + i;
                int segmentDuration = (i == totalSegments - 1)
                        ? duration - (i * SEGMENT_DURATION)
                        : SEGMENT_DURATION;

                String adSegmentName = String.format("advertisement_%d_%d.ts", segmentNumber, i);
                streamAds.put(currentSegment, new AdvertisementInfo(segmentPath, segmentDuration, adSegmentName, false));
            }
        }

        updatePlaylist(streamId);
        log.info("Registered advertisement for stream {} starting at segment {}, total duration: {}s",
                streamId, segmentNumber, duration);
    }

    public String getPlaylistContent(String streamId, String storageType) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, String> streamPlaylists = playlistContents.get(streamId);
            if (streamPlaylists == null) {
                return generateEmptyPlaylist(0);
            }

            String content = streamPlaylists.get(storageType.toLowerCase());
            if (content == null) {
                return generateEmptyPlaylist(0);
            }

            long duration = System.currentTimeMillis() - startTime;
            performanceLogger.info("Playlist content retrieved in {} ms for streamId: {}, storageType: {}",
                    duration, streamId, storageType);
            return content;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceLogger.error("Failed to get playlist content in {} ms for streamId: {}, storageType: {}",
                    duration, streamId, storageType);
            throw e;
        }
    }

    @CacheEvict(value = "segments", key = "#streamId")
    public synchronized void addSegment(String streamId, String segmentName) {
        long startTime = System.currentTimeMillis();
        try {
            int sequence = extractSequenceNumber(segmentName);
            TreeSet<Integer> sequences = streamSequences.computeIfAbsent(streamId, k -> new TreeSet<>());
            sequences.add(sequence);

            while (sequences.size() > MAX_SEGMENTS) {
                sequences.pollFirst();
            }

            updatePlaylist(streamId);

            long duration = System.currentTimeMillis() - startTime;
            performanceLogger.info("Segment added in {} ms for streamId: {}, segment: {}",
                    duration, streamId, segmentName);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            performanceLogger.error("Failed to add segment in {} ms for streamId: {}, segment: {}",
                    duration, streamId, segmentName);
            throw e;
        }
    }

    private void updatePlaylist(String streamId) {
        TreeSet<Integer> sequences = streamSequences.get(streamId);
        if (sequences == null || sequences.isEmpty()) {
            return;
        }

        int mediaSequence = sequences.first();
        List<StorageService> services = storageManager.getStoragesForStream(streamId);
        Map<String, String> playlists = playlistContents.computeIfAbsent(streamId, k -> new ConcurrentHashMap<>());
        Map<Integer, AdvertisementInfo> advertisements = advertisementSegments.getOrDefault(streamId, new ConcurrentHashMap<>());

        int maxDuration = SEGMENT_DURATION;
        for (AdvertisementInfo adInfo : advertisements.values()) {
            maxDuration = Math.max(maxDuration, adInfo.getDuration());
        }

        for (StorageService service : services) {
            StringBuilder playlist = new StringBuilder();
            playlist.append("#EXTM3U\n");
            playlist.append("#EXT-X-VERSION:3\n");
            playlist.append("#EXT-X-TARGETDURATION:").append(maxDuration).append("\n");
            playlist.append("#EXT-X-MEDIA-SEQUENCE:").append(mediaSequence).append("\n");
            playlist.append("#EXT-X-DISCONTINUITY-SEQUENCE:0\n");

            boolean wasAdvertisement = false;
            for (Integer sequence : sequences) {
                AdvertisementInfo adInfo = advertisements.get(sequence);

                if (adInfo != null) {
                    if (!wasAdvertisement) {
                        playlist.append("#EXT-X-DISCONTINUITY\n");
                    }
                    playlist.append("#EXTINF:").append(adInfo.getDuration()).append(".0,\n");
                    playlist.append(service.getSegmentUrl(streamId, adInfo.getSegmentName())).append("\n");
                    wasAdvertisement = true;
                } else {
                    if (wasAdvertisement) {
                        playlist.append("#EXT-X-DISCONTINUITY\n");
                    }
                    String segmentName = String.format("segment_%d.ts", sequence);
                    playlist.append("#EXTINF:").append(SEGMENT_DURATION).append(".0,\n");
                    playlist.append(service.getSegmentUrl(streamId, segmentName)).append("\n");
                    wasAdvertisement = false;
                }
            }

            playlists.put(service.getStorageType().toLowerCase(), playlist.toString());
        }
    }

    @CacheEvict(value = {"segments", "m3u8Urls"}, key = "#streamId")
    public void clearStreamCache(String streamId) {
        streamSequences.remove(streamId);
        playlistContents.remove(streamId);
        advertisementSegments.remove(streamId);
    }

    private String generateEmptyPlaylist(int mediaSequence) {
        return String.format("""
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:%d
            #EXT-X-MEDIA-SEQUENCE:%d
            """, SEGMENT_DURATION, mediaSequence);
    }

    private int extractSequenceNumber(String segmentName) {
        try {
            return Integer.parseInt(segmentName.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            log.warn("Failed to extract sequence number from segment name: {}", segmentName);
            return 0;
        }
    }
}
