package com.chunbaetour.domain.translation.repository;

import com.chunbaetour.domain.translation.entity.TranslationCache;
import com.chunbaetour.domain.translation.type.LanguageCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranslationCacheRepository extends JpaRepository<TranslationCache, Long> {

    // content_hash + target_language UNIQUE — DB 영구 캐시 조회
    Optional<TranslationCache> findByContentHashAndTargetLanguage(String contentHash, LanguageCode targetLanguage);
}
