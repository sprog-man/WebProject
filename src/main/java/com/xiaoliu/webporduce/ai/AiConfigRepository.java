package com.xiaoliu.webporduce.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiConfigRepository extends JpaRepository<AiConfig, UUID> {

    List<AiConfig> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    Optional<AiConfig> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<AiConfig> findFirstByOwnerIdAndDefaultConfigTrue(UUID ownerId);

    Optional<AiConfig> findFirstByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    Optional<AiConfig> findFirstByOwnerIdAndIdNotOrderByCreatedAtAsc(UUID ownerId, UUID id);

    long countByOwnerId(UUID ownerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AiConfig c set c.defaultConfig = false where c.ownerId = :ownerId and c.defaultConfig = true")
    void clearDefault(@Param("ownerId") UUID ownerId);
}
