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

    /**
     * 캐시에 저장되는 앱 DTO와 캐시 DTO가 실제 담는 JDK 타입만 역직렬화 허용 — 임의 gadget 클래스 차단.
     *
     * <p>{@code java.} 전체를 허용하면 {@code java.lang.Runtime}, {@code java.lang.ProcessBuilder},
     * {@code java.net.URLClassLoader} 같은 역직렬화 gadget까지 열려 RCE 벡터가 된다. 캐시 DTO가 실제 담는
     * 컬렉션/날짜/스칼라 패키지·타입만 좁게 허용한다. 새 캐시 타입 추가 시 여기에 명시적으로 등록한다.
     */
    public static PolymorphicTypeValidator cacheTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                // 앱 캐시 DTO 전체
                .allowIfSubType("com.chunbaetour.")
                // 컬렉션 (List/Map 및 구현체)
                .allowIfSubType("java.util.")
                // 날짜/시간 (LocalDate 등)
                .allowIfSubType("java.time.")
                // 스칼라 — java.lang 전체가 아닌 안전한 구체 타입만 (Runtime/ProcessBuilder 제외)
                .allowIfSubType(Number.class)
                .allowIfSubType(String.class)
                .allowIfSubType(Boolean.class)
                .allowIfSubType(Character.class)
                .allowIfSubType(Enum.class)
                .build();
    }

    /** 타입정보(@class)를 보존하는 Redis value 직렬화기. allowlist validator로 임의 타입 역직렬화를 차단한다. */
    public static GenericJacksonJsonRedisSerializer typePreservingSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(cacheTypeValidator())
                .build();
    }
}
