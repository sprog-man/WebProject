package com.xiaoliu.webporduce.image;

import com.xiaoliu.webporduce.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/image-ai")
public class ImageAiConfigController {

    private final ImageAiConfigService configService;

    public ImageAiConfigController(ImageAiConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/providers")
    public List<ImageProviderPresetResponse> providers() {
        return configService.presets();
    }

    @GetMapping("/configs")
    public List<ImageAiConfigResponse> list() {
        return configService.list(CurrentUser.require().getId());
    }

    @PostMapping("/configs")
    public ResponseEntity<ImageAiConfigResponse> create(@Valid @RequestBody ImageAiConfigRequest request) {
        ImageAiConfigResponse response = configService.create(CurrentUser.require().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/configs/{configId}")
    public ImageAiConfigResponse update(@PathVariable UUID configId,
                                        @Valid @RequestBody ImageAiConfigRequest request) {
        return configService.update(CurrentUser.require().getId(), configId, request);
    }

    @DeleteMapping("/configs/{configId}")
    public ResponseEntity<Void> delete(@PathVariable UUID configId) {
        configService.delete(CurrentUser.require().getId(), configId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/configs/{configId}/default")
    public ImageAiConfigResponse setDefault(@PathVariable UUID configId) {
        return configService.setDefault(CurrentUser.require().getId(), configId);
    }

    @PostMapping("/configs/{configId}/test")
    public ImageAiConfigTestResponse test(@PathVariable UUID configId) {
        return configService.test(CurrentUser.require().getId(), configId);
    }
}
