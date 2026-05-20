package com.chunbaetour.domain.payment.exception;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;

public class PaymentException extends BusinessException {

    public PaymentException(ErrorCode errorCode) {
        super(errorCode);
    }
}
