package com.streamsegmenter.service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class StorageManager {
    private final List<StorageService> storageServices;
    private final Map<String, List<StorageService>> streamStorages = new ConcurrentHashMap<>();

    public void registerStreamStorages(String streamId, List<String> types) {
        List<StorageService> services = storageServices.stream()
                .filter(service -> types.contains(service.getStorageType()))
                .toList();
        streamStorages.put(streamId, services);
    }

    public List<StorageService> getStoragesForStream(String streamId) {
        return streamStorages.getOrDefault(streamId, List.of(
                storageServices.stream()
                        .filter(s -> s.getStorageType().equals("LOCAL"))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No LOCAL storage available"))
        ));
    }

    public void removeStreamStorages(String streamId) {
        streamStorages.remove(streamId);
    }
}
