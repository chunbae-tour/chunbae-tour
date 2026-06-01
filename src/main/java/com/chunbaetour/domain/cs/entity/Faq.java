package com.chunbaetour.domain.cs.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "faqs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    // String 타입 — Admin이 카테고리를 자유롭게 입력하는 설계. 고정 목록이 확정되면 FaqCategory enum으로 전환 검토.
    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Builder
    private Faq(String question, String answer, String category) {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("question is required");
        if (answer == null || answer.isBlank()) throw new IllegalArgumentException("answer is required");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("category is required");
        this.question = question;
        this.answer = answer;
        this.category = category;
        this.isActive = true;
    }

    // FAQ 내용 수정 — null/blank 필드는 기존 값 유지
    public void update(String question, String answer, String category) {
        if (question != null && !question.isBlank()) this.question = question;
        if (answer != null && !answer.isBlank()) this.answer = answer;
        if (category != null && !category.isBlank()) this.category = category;
    }

    // 활성화 — @Transactional 경계 안 영속 상태에서 호출해야 updatedAt 갱신 보장
    public void activate() {
        this.isActive = true;
    }

    // 비활성화 (soft hide — USER 노출 제외) — @Transactional 경계 안 영속 상태에서 호출해야 updatedAt 갱신 보장
    public void deactivate() {
        this.isActive = false;
    }
}
