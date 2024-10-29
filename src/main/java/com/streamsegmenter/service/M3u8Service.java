package com.streamsegmenter.service;

import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class M3u8Service {
    private final StorageManager storageManager;
    private final Map<String, TreeSet<Integer>> streamSequences = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> playlistContents = new ConcurrentHashMap<>();

    private static final int SEGMENT_DURATION = 5;
    private static final int MAX_SEGMENTS = 6; // 30 seconds of content

    @Cacheable(value = "m3u8Urls", key = "#streamId", unless = "#result == null")
    public List<String> getM3u8Urls(String streamId) {
        List<String> urls = new ArrayList<>();
        List<StorageService> services = storageManager.getStoragesForStream(streamId);

        for (StorageService service : services) {
            urls.add(String.format("/api/stream/%s/%s/playlist.m3u8",
                    streamId, service.getStorageType().toLowerCase()));
        }
        return urls;
    }

    //@Cacheable(value = "segments", key = "{#streamId, #storageType}", unless = "#result == null")
    public String getPlaylistContent(String streamId, String storageType) {
        return playlistContents
                .computeIfAbsent(streamId, k -> new ConcurrentHashMap<>())
                .getOrDefault(storageType, generateEmptyPlaylist(0));
    }

    @CacheEvict(value = "segments", key = "#streamId")
    public synchronized void addSegment(String streamId, String segmentName) {
        int sequence = extractSequenceNumber(segmentName);
        TreeSet<Integer> sequences = streamSequences.computeIfAbsent(streamId, k -> new TreeSet<>());
        sequences.add(sequence);

        // Keep only the last MAX_SEGMENTS segments
        while (sequences.size() > MAX_SEGMENTS) {
            sequences.pollFirst();
        }

        updatePlaylists(streamId);
    }

    @CacheEvict(value = {"segments", "m3u8Urls"}, key = "#streamId")
    public void clearStreamCache(String streamId) {
        streamSequences.remove(streamId);
        playlistContents.remove(streamId);
    }

    private String generateEmptyPlaylist(int mediaSequence) {
        return String.format("""
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:%d
            #EXT-X-MEDIA-SEQUENCE:%d
            """, SEGMENT_DURATION, mediaSequence);
    }

    private synchronized void updatePlaylists(String streamId) {
        TreeSet<Integer> sequences = streamSequences.get(streamId);
        if (sequences == null || sequences.isEmpty()) {
            return;
        }

        int mediaSequence = sequences.first();
        List<StorageService> services = storageManager.getStoragesForStream(streamId);
        Map<String, String> playlists = playlistContents.computeIfAbsent(streamId, k -> new ConcurrentHashMap<>());

        for (StorageService service : services) {
            StringBuilder playlist = new StringBuilder();
            playlist.append("#EXTM3U\n");
            playlist.append("#EXT-X-VERSION:3\n");
            playlist.append("#EXT-X-TARGETDURATION:").append(SEGMENT_DURATION).append("\n");
            playlist.append("#EXT-X-MEDIA-SEQUENCE:").append(mediaSequence).append("\n");

            for (Integer sequence : sequences) {
                String segmentName = String.format("segment_%d.ts", sequence);
                playlist.append("#EXTINF:").append(SEGMENT_DURATION).append(".0,\n");
                playlist.append(service.getSegmentUrl(streamId, segmentName)).append("\n");
            }

            playlists.put(service.getStorageType().toLowerCase(), playlist.toString());
        }
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
