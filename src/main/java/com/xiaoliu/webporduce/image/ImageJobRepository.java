package com.xiaoliu.webporduce.image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImageJobRepository extends JpaRepository<ImageJob, UUID> {

    Optional<ImageJob> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<ImageJob> findTop50ByOwnerIdAndProjectIdOrderByCreatedAtDesc(UUID ownerId, UUID projectId);

    Optional<ImageJob> findFirstByProjectIdAndTargetPathAndStatusInOrderByCreatedAtDesc(
            UUID projectId,
            String targetPath,
            Collection<ImageJobStatus> statuses
    );

    long countByProjectIdAndStatusIn(UUID projectId, Collection<ImageJobStatus> statuses);

    List<ImageJob> findByStatusIn(Collection<ImageJobStatus> statuses);
}
