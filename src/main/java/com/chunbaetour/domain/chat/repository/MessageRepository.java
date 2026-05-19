package com.chunbaetour.domain.chat.repository;

import com.chunbaetour.domain.chat.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
}
