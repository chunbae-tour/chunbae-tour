package com.chunbaetour.domain.translation.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.translation.type.LanguageCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "translation_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_translation_cache_hash_lang",
                columnNames = {"content_hash", "target_language"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranslationCache extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 원문 SHA-256 해시 (hex 64자) — target_language와 함께 UNIQUE
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_language", nullable = false, length = 10)
    private LanguageCode targetLanguage;

    @Column(name = "translated_content", nullable = false, columnDefinition = "TEXT")
    private String translatedContent;

    @Builder
    private TranslationCache(String contentHash, LanguageCode targetLanguage, String translatedContent) {
        this.contentHash = contentHash;
        this.targetLanguage = targetLanguage;
        this.translatedContent = translatedContent;
    }
}
