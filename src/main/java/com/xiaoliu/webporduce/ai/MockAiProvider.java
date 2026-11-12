package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.xiaoliu.webporduce.project.PageSpecProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MockAiProvider implements AiProvider {

    private static final Pattern REPLACE_TITLE = Pattern.compile("(?:把)?标题(?:改成|换成|改为)[“\"']?(.+?)[”\"']?$");

    private final PageSpecProvider specProvider;
    private final ObjectMapper objectMapper;

    public MockAiProvider(PageSpecProvider specProvider, ObjectMapper objectMapper) {
        this.specProvider = specProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public AiResult generate(String projectName, JsonNode currentSpec, String message) {
        JsonNode spec = specProvider.defaultSpec();
        ((ObjectNode) spec.path("meta")).put("title", projectName);
        customizeForBrief(spec, message);
        return new AiResult(
                "我先按你的描述搭了一版可编辑的页面：大字号、克制的留白，再用一个暖色把视觉重心收住。\n\n现在可以直接点画布里的文字、图片和数字修改，右侧还能换主题、字号和版式。"
                        + demoNotice(),
                spec,
                true,
                "mock"
        );
    }

    @Override
    public AiResult edit(JsonNode currentSpec, String message) {
        JsonNode spec = currentSpec.deepCopy();
        List<String> changes = new ArrayList<>();
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);

        String accent = detectAccent(normalized);
        if (accent != null) {
            ((ObjectNode) spec.path("theme")).put("accent", accent);
            changes.add("强调色 " + accent);
        }

        String mode = detectTheme(normalized);
        if (mode != null) {
            ((ObjectNode) spec.path("theme")).put("mode", mode);
            changes.add("主题 " + mode);
        }

        if (normalized.contains("大一点") || normalized.contains("更大") || normalized.contains("大字号")) {
            int current = spec.path("theme").path("titleScale").asInt(100);
            int next = Math.min(125, current + 10);
            ((ObjectNode) spec.path("theme")).put("titleScale", next);
            changes.add("标题字号 " + next + "%");
        } else if (normalized.contains("小一点") || normalized.contains("更小")) {
            int current = spec.path("theme").path("titleScale").asInt(100);
            int next = Math.max(80, current - 10);
            ((ObjectNode) spec.path("theme")).put("titleScale", next);
            changes.add("标题字号 " + next + "%");
        }

        if (normalized.contains("居中")) {
            for (JsonNode section : spec.path("sections")) {
                if ("hero.split".equals(section.path("component").asText())) {
                    ((ObjectNode) section.path("layout")).put("align", "center");
                    changes.add("首屏居中排版");
                }
            }
        } else if (normalized.contains("左对齐") || normalized.contains("靠左")) {
            for (JsonNode section : spec.path("sections")) {
                if ("hero.split".equals(section.path("component").asText())) {
                    ((ObjectNode) section.path("layout")).put("align", "left");
                    changes.add("首屏左对齐");
                }
            }
        }

        if (normalized.contains("隐藏") && (normalized.contains("数据") || normalized.contains("数字"))) {
            if (removeSections(spec, "stats.row")) {
                changes.add("隐藏数据条");
            }
        }
        if (normalized.contains("隐藏") && normalized.contains("作品")) {
            if (removeSections(spec, "gallery.grid")) {
                changes.add("隐藏作品区");
            }
        }

        if (normalized.contains("换图") || normalized.contains("换一张图") || normalized.contains("图片换成")) {
            boolean replaced = replaceHeroImage(spec);
            if (replaced) {
                changes.add("首屏图片");
            }
        }

        if (normalized.contains("标题改成") || normalized.contains("标题换成") || normalized.contains("标题改为")) {
            Matcher matcher = REPLACE_TITLE.matcher(message.trim());
            if (matcher.find()) {
                String title = matcher.group(1).trim();
                if (!title.isEmpty() && title.length() <= 80) {
                    for (JsonNode section : spec.path("sections")) {
                        if ("hero.split".equals(section.path("component").asText())) {
                            ((ObjectNode) section.path("props")).put("title", title);
                            changes.add("首屏标题");
                            break;
                        }
                    }
                }
            }
        }

        if (normalized.contains("重新生成") || normalized.contains("换一版") || normalized.contains("重做")) {
            JsonNode fresh = specProvider.defaultSpec();
            ((ObjectNode) fresh.path("meta")).put("title", spec.path("meta").path("title").asText("新页面"));
            ((ObjectNode) fresh.path("theme")).put("accent", "#9d8cff");
            ((ObjectNode) fresh.path("theme")).put("mode", "forest");
            return new AiResult(
                    "换了一版更偏森林感的方案，结构和编辑方式保持一致。不喜欢的话可以按 Ctrl+Z 撤回。"
                            + demoNotice(),
                    fresh,
                    true,
                    "mock"
            );
        }

        boolean changed = !changes.isEmpty();
        String reply;
        if (changed) {
            reply = "已经同步到画布：" + String.join("、", changes)
                    + "。\n\n其他文案、图片和结构都保留了。你也可以直接点画布继续改，不必再描述一遍。"
                    + demoNotice();
        } else {
            reply = "我保留了你现在这一版。\n\n这个模式下，文字、颜色、字号、图片和布局都可以直接点选修改；右侧面板适合做全局调整。"
                    + demoNotice();
        }
        return new AiResult(reply, spec, changed, "mock");
    }

    private void customizeForBrief(JsonNode spec, String brief) {
        String text = brief == null ? "" : brief.toLowerCase(Locale.ROOT);
        ObjectNode meta = (ObjectNode) spec.path("meta");
        boolean foodOrDrink = text.contains("奶茶") || text.contains("茶饮") || text.contains("餐饮")
                || text.contains("甜品") || text.contains("烘焙");
        if (text.contains("咖啡")) {
            meta.put("description", "一间独立咖啡品牌，安静、有手作感。");
            setHero(spec, "MORNING", "把一杯咖啡，\n做成一段日常。", "从豆子到杯子，我们相信好的日常不需要大声解释。", "#c58a5a");
            setHeroPrompt(spec, "Editorial coffee brand still life: a handmade ceramic cup on a worn wooden counter, warm morning window light, soft steam, scattered coffee beans, tactile paper texture, quiet muted brown palette, no text, no logo.");
            setTheme(spec, "paper");
            setMenu(spec, "COFFEE MENU", "今日咖啡单", "豆子每天换，风味跟着季节走。", new String[][]{
                    {"手冲单品", "当日单一产区，干净、有层次。", "¥32", "今日"},
                    {"燕麦拿铁", "坚果调性，口感轻盈。", "¥28"},
                    {"冷萃美式", "低温慢萃，清醒但不尖锐。", "¥26"}
            });
        } else if (foodOrDrink) {
            meta.put("description", "一间有果香和山野气的茶饮品牌。");
            setHero(spec, "SHANLAN", "山野的清气，\n装进这一杯。", "当季水果、原叶茶和一点克制，做一杯喝完会想念的茶。", "#5aad8a");
            setHeroPrompt(spec, "Editorial tea brand photography: a tall glass of iced jasmine tea with fresh green grapes on a pale stone table, bright natural daylight, condensation droplets, soft green leaves, clean pastel palette, airy composition, no text, no logo.");
            setTheme(spec, "candy");
            setMenu(spec, "SIGNATURE MENU", "本季茶单", "门店每日限量，卖完就等明天。", new String[][]{
                    {"山岚茉莉", "茉莉绿茶、青提、少量海盐。", "¥22", "招牌"},
                    {"青梅乌龙", "碳焙乌龙、青梅、气泡。", "¥24", "限定"},
                    {"桂花米酿", "桂花乌龙、米酿、鲜奶。", "¥26"}
            });
        } else if (text.contains("摄影") || text.contains("作品集")) {
            meta.put("description", "以图像与留白为主角的摄影工作室。");
            setHero(spec, "FRAME", "光落下的地方，\n就是作品开始的地方。", "商业摄影、编辑影像与长期视觉项目。", "#e9c46a");
            setHeroPrompt(spec, "Cinematic editorial photograph of a quiet concrete studio interior, a single beam of late afternoon sunlight cutting across the wall, deep shadows, warm dust in the air, minimal composition, fine film grain, no people, no text, no logo.");
            setGalleryPrompts(spec, new String[]{
                    "Editorial portrait photograph of a ceramic artist in a sunlit studio, hands shaping clay, soft window light, muted earth tones, shallow depth of field, no text, no logo.",
                    "Architectural detail photograph of a brutalist concrete stairwell, strong geometric shadows, warm late-day light, quiet monumental mood, no people, no text, no logo.",
                    "Still life photograph of wildflowers in a handmade glass vase on a linen table, soft overcast daylight, delicate muted colors, tactile grain, no text, no logo."
            });
            setTheme(spec, "paper");
            setHeroVariant(spec, "full");
            setGalleryVariant(spec, "masonry");
        } else if (text.contains("ai") || text.contains("人工智能") || text.contains("科技")) {
            meta.put("description", "一个有未来感但不冰冷的 AI 产品。");
            setHero(spec, "NOVA", "让复杂的技术，\n变得可以触摸。", "为思考留出空间，让工具安静地退到内容之后。", "#75d4e8");
            setHeroPrompt(spec, "Abstract technology brand image: translucent glass planes floating in a deep blue-black space, thin cyan light lines, soft volumetric haze, precise studio reflections, calm futuristic mood, no text, no logo, no interface.");
            setTheme(spec, "ocean");
            setFeatures(spec, "BUILT FOR TEAMS", "把重复工作，\n交给系统。", "让团队把时间花在真正需要判断的地方。", new String[][]{
                    {"01", "可观测", "每一步都有输入、输出和耗时。"},
                    {"02", "可回滚", "工作流版本化，随时回到稳定状态。"},
                    {"03", "可扩展", "按团队自己的方式组合和扩展。"}
            });
        }
    }

    private void setHero(JsonNode spec, String brand, String title, String subtitle, String accent) {
        ((ObjectNode) spec.path("theme")).put("accent", accent);
        for (JsonNode section : spec.path("sections")) {
            String component = section.path("component").asText();
            if ("nav.basic".equals(component)) {
                ((ObjectNode) section.path("props")).put("brand", brand);
            }
            if ("hero.split".equals(component)) {
                ObjectNode props = (ObjectNode) section.path("props");
                props.put("eyebrow", brand + " STUDIO — 独立创意工作室");
                props.put("title", title);
                props.put("subtitle", subtitle);
                props.put("mediaLabel", brand + " / 2026");
            }
            if ("footer.basic".equals(component)) {
                ((ObjectNode) section.path("props")).put("brand", brand);
            }
        }
    }

    private void setHeroPrompt(JsonNode spec, String prompt) {
        for (JsonNode section : spec.path("sections")) {
            if ("hero.split".equals(section.path("component").asText())) {
                JsonNode image = section.path("props").path("image");
                if (image instanceof ObjectNode object) {
                    object.put("prompt", prompt);
                }
                return;
            }
        }
    }

    private void setGalleryPrompts(JsonNode spec, String[] prompts) {
        for (JsonNode section : spec.path("sections")) {
            if (!"gallery.grid".equals(section.path("component").asText())) {
                continue;
            }
            JsonNode items = section.path("props").path("items");
            for (int index = 0; index < items.size(); index++) {
                JsonNode image = items.get(index).path("image");
                if (image instanceof ObjectNode object) {
                    object.put("prompt", prompts[Math.min(index, prompts.length - 1)]);
                }
            }
            return;
        }
    }

    private void setTheme(JsonNode spec, String mode) {
        ((ObjectNode) spec.path("theme")).put("mode", mode);
    }

    private void setHeroVariant(JsonNode spec, String variant) {
        setVariant(spec, "hero.split", variant);
    }

    private void setGalleryVariant(JsonNode spec, String variant) {
        setVariant(spec, "gallery.grid", variant);
    }

    private void setVariant(JsonNode spec, String component, String variant) {
        for (JsonNode sectionNode : spec.path("sections")) {
            if (!(sectionNode instanceof ObjectNode section)) {
                continue;
            }
            if (component.equals(section.path("component").asText())) {
                ObjectNode layout = section.path("layout").isObject()
                        ? (ObjectNode) section.get("layout")
                        : objectMapper.createObjectNode();
                layout.put("variant", variant);
                section.set("layout", layout);
                return;
            }
        }
    }

    private void setMenu(JsonNode spec, String eyebrow, String title, String subtitle, String[][] items) {
        ArrayNode nodes = objectMapper.createArrayNode();
        for (String[] item : items) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("name", item[0]);
            node.put("description", item[1]);
            node.put("price", item[2]);
            if (item.length > 3 && !item[3].isBlank()) {
                node.put("tag", item[3]);
            }
            nodes.add(node);
        }
        ObjectNode props = objectMapper.createObjectNode();
        props.put("eyebrow", eyebrow);
        props.put("title", title);
        props.put("subtitle", subtitle);
        props.set("items", nodes);
        ObjectNode layout = objectMapper.createObjectNode();
        layout.put("columns", 2);
        replaceSection(spec, "gallery.grid", "menu.list", props, layout);
    }

    private void setFeatures(JsonNode spec, String eyebrow, String title, String subtitle, String[][] items) {
        ArrayNode nodes = objectMapper.createArrayNode();
        for (String[] item : items) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("tag", item[0]);
            node.put("title", item[1]);
            node.put("description", item[2]);
            nodes.add(node);
        }
        ObjectNode props = objectMapper.createObjectNode();
        props.put("eyebrow", eyebrow);
        props.put("title", title);
        props.put("subtitle", subtitle);
        props.set("items", nodes);
        ObjectNode layout = objectMapper.createObjectNode();
        layout.put("columns", 3);
        replaceSection(spec, "gallery.grid", "feature.cards", props, layout);
    }

    private boolean replaceSection(JsonNode spec, String fromComponent, String toComponent,
                                   ObjectNode props, ObjectNode layout) {
        for (JsonNode sectionNode : spec.path("sections")) {
            if (!(sectionNode instanceof ObjectNode section)) {
                continue;
            }
            if (fromComponent.equals(section.path("component").asText())) {
                section.put("component", toComponent);
                section.set("props", props);
                section.set("layout", layout);
                return true;
            }
        }
        return false;
    }

    private String detectAccent(String text) {
        if (text.contains("紫") || text.contains("violet")) {
            return "#9d8cff";
        }
        if (text.contains("青柠") || text.contains("绿")) {
            return "#c8f169";
        }
        if (text.contains("蓝") || text.contains("cyan")) {
            return "#75d4e8";
        }
        if (text.contains("粉")) {
            return "#ff9db4";
        }
        if (text.contains("金") || text.contains("黄")) {
            return "#e9c46a";
        }
        if (text.contains("暖") || text.contains("橘") || text.contains("橙") || text.contains("红")) {
            return "#ff6b3d";
        }
        return null;
    }

    private String detectTheme(String text) {
        if (text.contains("森林") || text.contains("forest")) {
            return "forest";
        }
        if (text.contains("奶油") || text.contains("浅色") || text.contains("亮色") || text.contains("纸张")) {
            return "paper";
        }
        if (text.contains("午夜") || text.contains("深色") || text.contains("暗色")) {
            return "ink";
        }
        return null;
    }

    private boolean removeSections(JsonNode spec, String component) {
        ArrayNode sections = (ArrayNode) spec.path("sections");
        boolean removed = false;
        for (int i = sections.size() - 1; i >= 0; i--) {
            if (component.equals(sections.get(i).path("component").asText())) {
                sections.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    private boolean replaceHeroImage(JsonNode spec) {
        for (JsonNode section : spec.path("sections")) {
            if ("hero.split".equals(section.path("component").asText())) {
                ObjectNode image = (ObjectNode) section.path("props").path("image");
                String current = image.path("src").asText("");
                String next = current.contains("visual-01")
                        ? "/presets/visual-02.svg"
                        : "/presets/visual-01.svg";
                image.put("src", next);
                image.put("alt", "Aurora 品牌视觉");
                return true;
            }
        }
        return false;
    }

    private String demoNotice() {
        return "\n\n（当前使用本地演示模型。到「AI 设置」配置并设为默认后，会使用真实模型生成。）";
    }
}
