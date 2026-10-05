package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByUserIdAndShowIdAndIdempotencyKey(
            Long userId,
            Long showId,
            String idempotencyKey);
}