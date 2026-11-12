package com.xiaoliu.webporduce.image;

import com.xiaoliu.webporduce.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/image-jobs")
public class ImageJobController {

    private final ImageJobService jobService;

    public ImageJobController(ImageJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public ImageJobListResponse list(@PathVariable UUID projectId) {
        return jobService.list(CurrentUser.require().getId(), projectId);
    }

    @PostMapping
    public ImageJobListResponse create(@PathVariable UUID projectId,
                                       @Valid @RequestBody CreateImageJobsRequest request) {
        return jobService.create(CurrentUser.require().getId(), projectId, request.items());
    }
}
