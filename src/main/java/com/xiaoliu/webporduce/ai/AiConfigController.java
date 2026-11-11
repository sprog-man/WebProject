package com.xiaoliu.webporduce.ai;

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
@RequestMapping("/api/ai")
public class AiConfigController {

    private final AiConfigService configService;

    public AiConfigController(AiConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/providers")
    public List<AiProviderPresetResponse> providers() {
        return configService.presets();
    }

    @GetMapping("/configs")
    public List<AiConfigResponse> list() {
        return configService.list(CurrentUser.require().getId());
    }

    @PostMapping("/configs")
    public ResponseEntity<AiConfigResponse> create(@Valid @RequestBody AiConfigRequest request) {
        AiConfigResponse response = configService.create(CurrentUser.require().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/configs/{configId}")
    public AiConfigResponse update(@PathVariable UUID configId,
                                   @Valid @RequestBody AiConfigRequest request) {
        return configService.update(CurrentUser.require().getId(), configId, request);
    }

    @DeleteMapping("/configs/{configId}")
    public ResponseEntity<Void> delete(@PathVariable UUID configId) {
        configService.delete(CurrentUser.require().getId(), configId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/configs/{configId}/default")
    public AiConfigResponse setDefault(@PathVariable UUID configId) {
        return configService.setDefault(CurrentUser.require().getId(), configId);
    }

    @PostMapping("/configs/{configId}/test")
    public AiConfigTestResponse test(@PathVariable UUID configId) {
        return configService.test(CurrentUser.require().getId(), configId);
    }
}
