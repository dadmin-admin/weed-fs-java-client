package com.scoopit.weedfs.client.caching;

import com.scoopit.weedfs.client.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by fbelov on 08.04.15.
 */
public class TimeBasedLookupCache implements LookupCache {

    private static final Logger log = LoggerFactory.getLogger(TimeBasedLookupCache.class);

    private ConcurrentHashMap<Long, List<Location>> cache = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Long, Long> cacheAccessTime = new ConcurrentHashMap<>();
    private final int invalidateInMillis;

    public TimeBasedLookupCache(int invalidateInSeconds) {
        this.invalidateInMillis = invalidateInSeconds*1000;
    }

    @Override
    public void invalidate() {
        cache = new ConcurrentHashMap<>();
        cacheAccessTime = new ConcurrentHashMap<>();
    }

    @Override
    public void invalidate(long volumeId) {
        cache.remove(volumeId);
        cacheAccessTime.remove(volumeId);
    }

    @Override
    public synchronized List<Location> lookup(long volumeId) {
        Long lastTimeAccessed = cacheAccessTime.get(volumeId);

        if (lastTimeAccessed == null || (System.currentTimeMillis() - lastTimeAccessed) < invalidateInMillis) {
            return cache.get(volumeId);
        } else {
            log.debug("Invalidating location for volume {}", volumeId);

            touchCache(volumeId);

            return null; //to reload location value
        }
    }

    @Override
    public void setLocation(long volumeId, List<Location> locations) {
        if (locations != null) {
            cache.put(volumeId, locations);
            touchCache(volumeId);
        }
    }

    private void touchCache(long volumeId) {
        cacheAccessTime.put(volumeId, System.currentTimeMillis());
    }

}
