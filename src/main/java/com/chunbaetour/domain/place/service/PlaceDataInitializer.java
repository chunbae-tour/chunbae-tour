package com.chunbaetour.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceDataInitializer implements CommandLineRunner {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(String... args) {
        log.info("Initializing Place Geospatial Data in Redis...");
        String key = "geo:places";

        // Dummy Data: (longitude, latitude, placeId)
        // 경복궁: 126.9770, 37.5796
        stringRedisTemplate.opsForGeo().add(key, new org.springframework.data.geo.Point(126.9770, 37.5796), "1");
        // N서울타워: 126.9882, 37.5511
        stringRedisTemplate.opsForGeo().add(key, new org.springframework.data.geo.Point(126.9882, 37.5511), "2");
        // 명동: 126.9822, 37.5636
        stringRedisTemplate.opsForGeo().add(key, new org.springframework.data.geo.Point(126.9822, 37.5636), "3");

        log.info("Place Geospatial Data initialized successfully.");
    }
}
