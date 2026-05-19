package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceGeoDataLoader {

    private final PlaceRepository placeRepository;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String GEO_KEY = "geo:places";

    @PostConstruct
    @Transactional(readOnly = true)
    public void initGeoData() {
        try {
            // Redis에 데이터가 없을 경우에만 적재
            Long size = stringRedisTemplate.opsForZSet().size(GEO_KEY);
            if (size == null || size == 0) {
                log.info("Initializing Redis Geospatial Data for Places...");
                // Fetch only active places instead of all places
                List<Place> places = placeRepository.findAll().stream()
                        .filter(p -> p.getStatus() == com.chunbaetour.domain.place.type.PlaceStatus.ACTIVE)
                        .toList();
                int count = 0;
                for (Place place : places) {
                    if (place.getLng() != null && place.getLat() != null) {
                        stringRedisTemplate.opsForGeo().add(GEO_KEY,
                                new Point(place.getLng().doubleValue(), place.getLat().doubleValue()),
                                place.getId().toString());
                        count++;
                    }
                }
                log.info("Loaded {} places into Redis Geo.", count);
            } else {
                log.info("Redis Geo Data already exists. Skipping initialization.");
            }
        } catch (Exception e) {
            log.error("Failed to load Redis Geo Data: ", e);
        }
    }
}
