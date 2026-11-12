package com.xiaoliu.webporduce.image;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImageAiConfigRepository extends JpaRepository<ImageAiConfig, UUID> {

    List<ImageAiConfig> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    Optional<ImageAiConfig> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<ImageAiConfig> findFirstByOwnerIdAndDefaultConfigTrue(UUID ownerId);

    Optional<ImageAiConfig> findFirstByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    Optional<ImageAiConfig> findFirstByOwnerIdAndIdNotOrderByCreatedAtAsc(UUID ownerId, UUID id);

    long countByOwnerId(UUID ownerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ImageAiConfig c set c.defaultConfig = false where c.ownerId = :ownerId and c.defaultConfig = true")
    void clearDefault(@Param("ownerId") UUID ownerId);
}
