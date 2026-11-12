package com.xiaoliu.webporduce.project;

import tools.jackson.databind.JsonNode;
import com.xiaoliu.webporduce.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PageSpecValidator {

    public static final Set<String> COMPONENTS = Set.of(
            "nav.basic",
            "hero.split",
            "gallery.grid",
            "stats.row",
            "feature.cards",
            "menu.list",
            "quote.block",
            "cta.banner",
            "footer.basic"
    );

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public void validate(JsonNode root) {
        if (root == null || !root.isObject()) {
            invalid("页面数据必须是 JSON 对象");
        }
        if (!root.path("version").canConvertToInt() || root.path("version").asInt() < 1) {
            invalid("页面版本不正确");
        }

        JsonNode meta = root.path("meta");
        if (!meta.isObject() || !meta.path("title").isTextual()) {
            invalid("页面缺少标题");
        }
        if (meta.path("title").asText().length() > 160) {
            invalid("页面标题过长");
        }
        if (meta.has("description") && !meta.path("description").isTextual()) {
            invalid("页面描述格式不正确");
        }

        validateTheme(root.path("theme"));

        JsonNode sections = root.path("sections");
        if (!sections.isArray()) {
            invalid("页面区块格式不正确");
        }
        if (sections.size() > 12) {
            invalid("页面区块不能超过 12 个");
        }

        Set<String> ids = new HashSet<>();
        for (JsonNode section : sections) {
            if (!section.isObject()) {
                invalid("区块格式不正确");
            }
            String id = section.path("id").asText("");
            String component = section.path("component").asText("");
            if (id.isBlank() || id.length() > 60 || !ids.add(id)) {
                invalid("区块 id 不正确或重复");
            }
            if (!COMPONENTS.contains(component)) {
                invalid("不支持的区块类型：" + component);
            }
            if (!section.path("props").isObject()) {
                invalid("区块内容格式不正确");
            }
            if (section.has("layout") && !section.path("layout").isObject()) {
                invalid("区块布局格式不正确");
            }
            validateSectionImages(section, component);
        }

        if (root.has("fieldMeta") && !root.path("fieldMeta").isObject()) {
            invalid("字段元数据格式不正确");
        }
    }

    private void validateSectionImages(JsonNode section, String component) {
        if ("hero.split".equals(component)) {
            validateImage(section.path("props").path("image"), false);
        } else if ("gallery.grid".equals(component)) {
            JsonNode items = section.path("props").path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    validateImage(item.path("image"), false);
                }
            }
        }
    }

    private void validateImage(JsonNode image, boolean required) {
        if (image == null || image.isMissingNode() || image.isNull()) {
            if (required) {
                invalid("图片数据不能为空");
            }
            return;
        }
        if (!image.isObject()) {
            invalid("图片数据格式不正确");
        }
        if (image.has("src") && !image.path("src").isTextual()) {
            invalid("图片地址格式不正确");
        }
        if (image.has("alt") && !image.path("alt").isTextual()) {
            invalid("图片说明格式不正确");
        }
        if (image.has("fit")) {
            String fit = image.path("fit").asText("");
            if (!Set.of("cover", "contain").contains(fit)) {
                invalid("图片展示方式不正确");
            }
        }
        if (image.has("assetId") && !image.path("assetId").isTextual()) {
            invalid("图片资产格式不正确");
        }
        if (image.has("prompt")) {
            JsonNode prompt = image.path("prompt");
            if (!prompt.isTextual()) {
                invalid("图像提示词格式不正确");
            }
            if (prompt.asText("").length() > 1500) {
                invalid("图像提示词不能超过 1500 个字符");
            }
        }
    }

    private void validateTheme(JsonNode theme) {
        if (!theme.isObject()) {
            invalid("主题格式不正确");
        }
        String mode = theme.path("mode").asText("");
        if (!Set.of("ink", "paper", "forest", "ocean", "candy", "mono").contains(mode)) {
            invalid("不支持的主题模式");
        }
        String accent = theme.path("accent").asText("");
        if (!HEX_COLOR.matcher(accent).matches()) {
            invalid("强调色格式不正确");
        }
        validateInt(theme, "titleScale", 60, 160);
        validateInt(theme, "bodyScale", 60, 160);
        validateInt(theme, "radius", 0, 40);
        validateInt(theme, "spacing", 60, 160);
        String font = theme.path("font").asText("");
        if (!Set.of("serif", "sans").contains(font)) {
            invalid("字体设置不正确");
        }
    }

    private void validateInt(JsonNode theme, String field, int min, int max) {
        JsonNode node = theme.path(field);
        if (!node.canConvertToInt()) {
            invalid(field + " 必须是数字");
        }
        int value = node.asInt();
        if (value < min || value > max) {
            invalid(field + " 超出允许范围");
        }
    }

    private void invalid(String message) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SPEC", message);
    }
}
