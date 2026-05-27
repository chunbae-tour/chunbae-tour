package com.chunbaetour.domain.store.repository;

import com.chunbaetour.domain.store.entity.UserItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {
}
