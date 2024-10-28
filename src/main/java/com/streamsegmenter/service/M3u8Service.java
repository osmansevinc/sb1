package com.streamsegmenter.service;

import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class M3u8Service {
    private final StorageManager storageManager;
    private final ConcurrentHashMap<String, LinkedList<String>> streamSegments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, String>> playlistContents = new ConcurrentHashMap<>();
    private static final int BUFFER_WINDOW_SECONDS = 30;
    private static final int SEGMENT_DURATION = 5;
    private static final int SEGMENTS_IN_BUFFER = BUFFER_WINDOW_SECONDS / SEGMENT_DURATION;

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

    @Cacheable(value = "segments", key = "{#streamId, #storageType}", unless = "#result == null")
    public String getPlaylistContent(String streamId, String storageType) {
        return playlistContents.getOrDefault(streamId, new ConcurrentHashMap<>())
                .getOrDefault(storageType, generateEmptyPlaylist(0));
    }

    @CacheEvict(value = "segments", key = "#streamId")
    public synchronized void addSegment(String streamId, String segmentName) {
        LinkedList<String> segments = streamSegments.computeIfAbsent(streamId, k -> new LinkedList<>());
        segments.addLast(segmentName);
        updatePlaylists(streamId);
    }

    @CacheEvict(value = {"segments", "m3u8Urls"}, key = "#streamId")
    public void clearStreamCache(String streamId) {
        streamSegments.remove(streamId);
        playlistContents.remove(streamId);
    }

    private String generateEmptyPlaylist(int mediaSequence) {
        StringBuilder playlist = new StringBuilder();
        playlist.append("#EXTM3U\n");
        playlist.append("#EXT-X-VERSION:3\n");
        playlist.append("#EXT-X-TARGETDURATION:5\n");
        playlist.append(String.format("#EXT-X-MEDIA-SEQUENCE:%d\n", mediaSequence));
        return playlist.toString();
    }

    private synchronized void updatePlaylists(String streamId) {
        LinkedList<String> segments = streamSegments.get(streamId);
        if (segments == null || segments.isEmpty()) {
            return;
        }

        int totalSegments = segments.size();
        int startIndex = Math.max(0, totalSegments - SEGMENTS_IN_BUFFER);
        int mediaSequence = extractSequenceNumber(segments.get(startIndex));

        List<StorageService> services = storageManager.getStoragesForStream(streamId);
        Map<String, String> playlists = playlistContents.computeIfAbsent(streamId, k -> new ConcurrentHashMap<>());

        for (StorageService service : services) {
            StringBuilder playlist = new StringBuilder();
            playlist.append("#EXTM3U\n");
            playlist.append("#EXT-X-VERSION:3\n");
            playlist.append("#EXT-X-TARGETDURATION:5\n");
            playlist.append(String.format("#EXT-X-MEDIA-SEQUENCE:%d\n", mediaSequence));

            for (String segment : segments) {
                playlist.append("#EXTINF:5.0,\n");
                playlist.append(service.getSegmentUrl(streamId, segment)).append("\n");
            }

            playlists.put(service.getStorageType().toLowerCase(), playlist.toString());
        }
    }

    private int extractSequenceNumber(String segmentName) {
        try {
            return Integer.parseInt(segmentName.substring(8, segmentName.length() - 3));
        } catch (Exception e) {
            log.warn("Failed to extract sequence number from segment name: {}", e.getMessage());
            return 0;
        }
    }
}
