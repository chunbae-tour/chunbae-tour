package com.chunbaetour.domain.common.repository;

import com.chunbaetour.domain.common.entity.CommonErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommonErrorLogRepository extends JpaRepository<CommonErrorLog, Long> {
}
