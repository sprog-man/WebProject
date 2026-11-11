package com.xiaoliu.webporduce.asset;

import com.xiaoliu.webporduce.common.ApiException;
import com.xiaoliu.webporduce.project.Project;
import com.xiaoliu.webporduce.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AssetService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif"
    );
    private static final long MAX_SIZE = 8L * 1024 * 1024;

    private final AssetRepository assetRepository;
    private final ProjectRepository projectRepository;
    private final Path storageRoot;

    public AssetService(AssetRepository assetRepository,
                        ProjectRepository projectRepository,
                        @Value("${app.storage.root:./data/uploads}") String storageRoot) {
        this.assetRepository = assetRepository;
        this.projectRepository = projectRepository;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public AssetResponse upload(UUID ownerId, UUID projectId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择一张图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过 8MB");
        }
        String mimeType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(mimeType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "只支持 PNG、JPG、WebP、GIF 图片");
        }
        if (projectId != null) {
            Project project = projectRepository.findByIdAndOwnerId(projectId, ownerId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "页面不存在"));
            if (!project.getOwnerId().equals(ownerId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "没有访问权限");
            }
        }

        String extension = extensionFor(mimeType);
        String storageKey = ownerId + "/" + UUID.randomUUID() + extension;
        Path target = storageRoot.resolve(storageKey).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "文件路径不正确");
        }

        Integer width = null;
        Integer height = null;
        try (InputStream stream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(stream);
            if (image != null) {
                width = image.getWidth();
                height = image.getHeight();
            }
        } catch (Exception ignored) {
            // WebP may not have an ImageIO reader in the JDK. Dimensions are optional.
        }

        try {
            Files.createDirectories(target.getParent());
            try (InputStream stream = file.getInputStream()) {
                Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败");
        }

        Asset asset = new Asset(
                ownerId,
                projectId,
                safeName(file.getOriginalFilename()),
                storageKey,
                mimeType,
                file.getSize(),
                width,
                height
        );
        assetRepository.save(asset);
        return toResponse(asset);
    }

    public Asset requireOwned(UUID ownerId, UUID assetId) {
        return assetRepository.findByIdAndOwnerId(assetId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "图片不存在"));
    }

    public Path pathOf(Asset asset) {
        Path path = storageRoot.resolve(asset.getStorageKey()).normalize();
        if (!path.startsWith(storageRoot) || !Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "图片文件不存在");
        }
        return path;
    }

    public AssetResponse toResponse(Asset asset) {
        return new AssetResponse(
                asset.getId(),
                "/api/assets/" + asset.getId() + "/content",
                asset.getOriginalName(),
                asset.getMimeType(),
                asset.getByteSize(),
                asset.getWidth(),
                asset.getHeight()
        );
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> "";
        };
    }

    private String safeName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "image";
        }
        String name = originalName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
