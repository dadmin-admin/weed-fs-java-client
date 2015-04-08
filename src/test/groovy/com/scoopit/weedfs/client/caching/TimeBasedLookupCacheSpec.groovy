package com.scoopit.weedfs.client.caching

import com.scoopit.weedfs.client.Location
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Created by fbelov on 08.04.15.
 */
class TimeBasedLookupCacheSpec extends Specification {

    private executorService = Executors.newCachedThreadPool();

    def "should lookup during cache period"() {
        when:
        def invalidateCacheInSeconds = 6
        def locationsByVolumes = locationsByVolumes
        def cache = new TimeBasedLookupCache(invalidateCacheInSeconds)

        locationsByVolumes.each { k, v ->
            cache.setLocation(k, v)
        }

        then:
        def futures = []

        5.times {
            futures << executorService.submit({
                    def calls = 0
                    def started = System.currentTimeMillis()

                    while ((System.currentTimeMillis() - started) < (invalidateCacheInSeconds - 1)*1000) {
                        locationsByVolumes.each { k, v ->
                            if (cache.lookup(k) != v) {
                                throw new IllegalStateException("k ${cache.lookup(k)} != v ${v}")
                            } else {
                                calls++
                            }
                        }

                        sleep(100)
                    }

                    assert calls > 0
                })

        }

        futures.each {
            it.get()
        }
    }

    def "each location should be reset only once"() {
        when:
        def invalidateCacheInSeconds = 5
        def locationsByVolumes = locationsByVolumes
        def cache = new TimeBasedLookupCache(invalidateCacheInSeconds)

        locationsByVolumes.each { k, v ->
            cache.setLocation(k, v)
        }

        then:
        def futures = []

        10.times {
            futures << executorService.submit({
                def callsBefore = 0
                def callsAfter = 0
                def started = System.currentTimeMillis()

                while ((System.currentTimeMillis() - started) < (invalidateCacheInSeconds + (invalidateCacheInSeconds - 1))*1000) {
                    for (def e : locationsByVolumes) {
                        def cached = cache.lookup(e.key)

                        if (cached == null) {
                            return e.key
                        } else {
                            assert cached == e.value

                            if ((System.currentTimeMillis() - started) < (invalidateCacheInSeconds)*1000) {
                                callsBefore++
                            } else {
                                callsAfter++
                            }
                        }
                    }

                    sleep(100)
                }

                assert callsBefore > 0
                assert callsAfter > 0

                return -1
            } as Callable<Integer>)

        }

        def expectedVolumes = locationsByVolumes.keySet()
        def resetVolumes = []

        futures.each {
            def answer = it.get()
            if (answer != -1) {
                resetVolumes << answer
            }
        }

        assert resetVolumes.size() == expectedVolumes.size()
        assert (resetVolumes as Set) == expectedVolumes
    }

    def "should return new location"() {
        when:
        def invalidateCacheInSeconds = 5
        def locationsByVolumes = locationsByVolumes
        def cache = new TimeBasedLookupCache(invalidateCacheInSeconds)

        locationsByVolumes.each { k, v ->
            cache.setLocation(k, v)
        }

        then:
        def futures = []

        10.times {
            futures << executorService.submit({
                def callsBefore = 0
                def callsAfter = 0
                def started = System.currentTimeMillis()

                while ((System.currentTimeMillis() - started) < (invalidateCacheInSeconds + (invalidateCacheInSeconds - 1))*1000) {
                    for (def e : locationsByVolumes) {
                        def cached = cache.lookup(e.key)

                        if (cached == null) {
                            cache.setLocation(e.key, getNewLocationForVolume(e.key))
                        } else {
                            if ((System.currentTimeMillis() - started) < (invalidateCacheInSeconds)*1000) {
                                assert cached == e.value
                                callsBefore++
                            } else {
                                if (cached != e.value) {
                                    assert cached == [new Location(url: e.key)]
                                    callsAfter++
                                }
                            }
                        }
                    }

                    sleep(100)
                }

                assert callsBefore > 0
                assert callsAfter > 0
                return true
            } as Callable<Integer>)

        }

        assert futures.every { it.get() }
    }

    private getLocationsByVolumes() {
        return [(1L): [new Location(url: "http://ya.ru")], (2L): [new Location(url: "http://google.com"), new Location(url: "http://google.ru")]]
    }

    private getNewLocationForVolume(Long volumenId) {
        return [new Location(url: volumenId)]
    }
}
