package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.jwt.TokenWithId;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    @Transactional(readOnly = true)
    public TokenPair login(String loginId, String password, Role requiredRole) {
        String email = loginId.toLowerCase(Locale.ROOT);

        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!passwordHasher.matches(password, account.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (account.getRole() != requiredRole) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        String accessToken = tokenIssuer.issueAccess(account.getId(), account.getRole(), account.getEmail());
        TokenWithId refresh = tokenIssuer.issueRefresh(account.getId());
        return new TokenPair(accessToken, refresh.token(), refresh.tokenId(), account.getRole());
    }
}
