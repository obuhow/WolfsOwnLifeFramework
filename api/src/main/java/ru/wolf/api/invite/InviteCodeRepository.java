package ru.wolf.api.invite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

    Optional<InviteCode> findByCode(String code);

    @Modifying
    @Query("UPDATE InviteCode ic SET ic.usedCount = ic.usedCount + 1 WHERE ic.code = :code AND ic.usedCount < ic.maxUses AND ic.revokedAt IS NULL AND (ic.expiresAt IS NULL OR ic.expiresAt > CURRENT_TIMESTAMP)")
    int incrementUsedCountIfValid(@Param("code") String code);

    List<InviteCode> findAllByOrderByCreatedAtDesc();
}