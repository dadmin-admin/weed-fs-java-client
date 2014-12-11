package com.scoopit.weedfs.client.caching;

import com.scoopit.weedfs.client.Location;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MapLookupCache implements LookupCache {
    ConcurrentHashMap<Long, List<Location>> cache = new ConcurrentHashMap<>();

    @Override
    public void invalidate() {
        cache = new ConcurrentHashMap<>();
    }

    @Override
    public void invalidate(long volumeId) {
        cache.remove(volumeId);
    }

    @Override
    public List<Location> lookup(long volumeId) {
        return cache.get(volumeId);
    }

    @Override
    public void setLocation(long volumeId, List<Location> locations) {
        if (locations != null) {
            cache.put(volumeId, locations);
        }
    }

}
