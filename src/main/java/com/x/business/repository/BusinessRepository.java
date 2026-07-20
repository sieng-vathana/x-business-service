package com.x.business.repository;

import com.x.business.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessRepository extends JpaRepository<Business, Long> {
    boolean existsByCode(String code);

    List<Business> findAllByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);
}
