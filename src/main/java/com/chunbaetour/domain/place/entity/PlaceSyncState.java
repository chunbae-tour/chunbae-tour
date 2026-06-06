package com.chunbaetour.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관광지 증분 동기화 상태(KAN-221). 단일 행(id=1)만 사용한다.
 * {@code lastModifiedTime} = 마지막 동기화에서 처리한 가장 최신 modifiedtime(yyyyMMddHHmmss).
 * 다음 동기화는 이 값보다 큰(이후) 변경분만 수집한다.
 */
@Entity
@Table(name = "place_sync_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceSyncState {

    /** 단일 행 고정 PK. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** 마지막으로 동기화한 최신 modifiedtime(yyyyMMddHHmmss). 최초 동기화 전이면 null. */
    @Column(name = "last_modified_time", length = 14)
    private String lastModifiedTime;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 최초 상태 행 생성(id=1). */
    public static PlaceSyncState init() {
        PlaceSyncState state = new PlaceSyncState();
        state.id = SINGLETON_ID;
        state.updatedAt = LocalDateTime.now();
        return state;
    }

    /** 동기화 완료 후 최신 modifiedtime 갱신. */
    public void updateLastModifiedTime(String lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
        this.updatedAt = LocalDateTime.now();
    }
}
