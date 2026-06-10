package com.chunbaetour.domain.common.config;

import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Redis value 직렬화기 팩토리 (KAN-264).
 *
 * <p>캐시 value에 다형성 타입정보({@code @class})를 기록하도록 default typing을 활성화한 직렬화기를 만든다.
 * 타입정보가 없으면 캐시 HIT 시 JSON이 구체 타입(예: {@code FestivalCacheData})이 아니라
 * {@link java.util.LinkedHashMap}으로 역직렬화돼 캐스팅에서 {@code ClassCastException}이 발생한다.
 *
 * <p>{@code new GenericJacksonJsonRedisSerializer(ObjectMapper)} 생성자는 default typing을 켜지 않으므로
 * 사용하지 않는다. 임의 클래스 역직렬화(gadget) 위험을 줄이기 위해 allowlist 기반
 * {@link PolymorphicTypeValidator}로 앱/JDK 타입만 허용한다.
 */
public final class RedisJsonSerializerFactory {

    private RedisJsonSerializerFactory() {
    }

    /** 캐시에 저장되는 앱 DTO와 JDK 표준 타입만 역직렬화 허용 — 임의 gadget 클래스 차단. */
    public static PolymorphicTypeValidator cacheTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.chunbaetour.")
                .allowIfSubType("java.")
                .build();
    }

    /** 타입정보(@class)를 보존하는 Redis value 직렬화기. allowlist validator로 임의 타입 역직렬화를 차단한다. */
    public static GenericJacksonJsonRedisSerializer typePreservingSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(cacheTypeValidator())
                .build();
    }
}
