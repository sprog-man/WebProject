package com.xiaoliu.webporduce.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRevisionRepository extends JpaRepository<ProjectRevision, UUID> {
    List<ProjectRevision> findAllByProjectIdOrderByRevisionNumberDesc(UUID projectId);
    Optional<ProjectRevision> findFirstByProjectIdOrderByRevisionNumberDesc(UUID projectId);
}
