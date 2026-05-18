package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.auth.event.UserRegisteredEvent;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Account signup(SignupRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        String nickname = request.nickname();

        if (accountRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (accountRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        String hashed = passwordHasher.hash(request.password());
        Account account = Account.registerUser(email, hashed, nickname, request.phoneNumber());
        Account saved;
        try {
            saved = accountRepository.save(account);
            accountRepository.flush();
        } catch (DataIntegrityViolationException e) {
            if (accountRepository.existsByEmail(email)) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            if (accountRepository.existsByNickname(nickname)) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            throw e;
        }

        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId(), saved.getEmail(), saved.getNickname()));
        return saved;
    }
}
