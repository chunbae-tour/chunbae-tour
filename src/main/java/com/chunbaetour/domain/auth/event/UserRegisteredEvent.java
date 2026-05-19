package com.chunbaetour.domain.auth.event;

public record UserRegisteredEvent(Long userId, String email, String nickname) {
}
