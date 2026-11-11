package com.xiaoliu.webporduce.asset;

import com.xiaoliu.webporduce.security.CurrentUser;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping
    public AssetResponse upload(@RequestParam(value = "projectId", required = false) UUID projectId,
                                @RequestParam("file") MultipartFile file) {
        return assetService.upload(CurrentUser.require().getId(), projectId, file);
    }

    @GetMapping("/{assetId}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID assetId) {
        Asset asset = assetService.requireOwned(CurrentUser.require().getId(), assetId);
        Resource resource = new FileSystemResource(assetService.pathOf(asset));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getMimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(resource);
    }
}
