package com.xiaoliu.webporduce.project;

import com.xiaoliu.webporduce.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectSummary> list() {
        return projectService.list(CurrentUser.require().getId());
    }

    @PostMapping
    public ResponseEntity<ProjectDetail> create(@Valid @RequestBody CreateProjectRequest request) {
        ProjectDetail detail = projectService.create(CurrentUser.require().getId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
    }

    @GetMapping("/{projectId}")
    public ProjectDetail get(@PathVariable UUID projectId) {
        return projectService.get(CurrentUser.require().getId(), projectId);
    }

    @PatchMapping("/{projectId}")
    public ProjectSummary rename(@PathVariable UUID projectId,
                                 @Valid @RequestBody RenameProjectRequest request) {
        return projectService.rename(CurrentUser.require().getId(), projectId, request.name());
    }

    @PutMapping("/{projectId}/draft")
    public SaveDraftResponse saveDraft(@PathVariable UUID projectId,
                                       @Valid @RequestBody SaveDraftRequest request) {
        return projectService.saveDraft(
                CurrentUser.require().getId(),
                projectId,
                request.spec(),
                request.version()
        );
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId) {
        projectService.delete(CurrentUser.require().getId(), projectId);
        return ResponseEntity.noContent().build();
    }
}
