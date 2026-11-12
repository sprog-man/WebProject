package com.xiaoliu.webporduce.ai;

import com.xiaoliu.webporduce.project.PageSpecProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PageSpecProvider specProvider = new PageSpecProvider(objectMapper);
    private final MockAiProvider provider = new MockAiProvider(specProvider, objectMapper);

    @Test
    void teaBriefUsesMenuAndCandyTheme() {
        AiResult result = provider.generate("山岚茶事", specProvider.blankSpec("山岚茶事"), "为一间茶饮品牌做官网");

        assertThat(result.spec().path("theme").path("mode").asText()).isEqualTo("candy");
        assertThat(componentNames(result.spec())).contains("menu.list").doesNotContain("gallery.grid");
    }

    @Test
    void aiBriefUsesFeatureCardsAndOceanTheme() {
        AiResult result = provider.generate("Northstar", specProvider.blankSpec("Northstar"), "为 AI 工作流平台做官网");

        assertThat(result.spec().path("theme").path("mode").asText()).isEqualTo("ocean");
        assertThat(componentNames(result.spec())).contains("feature.cards").doesNotContain("gallery.grid");
    }

    @Test
    void photoBriefUsesFullHeroAndMasonryGallery() {
        AiResult result = provider.generate("FRAME", specProvider.blankSpec("FRAME"), "为摄影工作室做官网");
        JsonNode sections = result.spec().path("sections");

        JsonNode hero = findSection(sections, "hero.split");
        JsonNode gallery = findSection(sections, "gallery.grid");

        assertThat(hero.path("layout").path("variant").asText()).isEqualTo("full");
        assertThat(gallery.path("layout").path("variant").asText()).isEqualTo("masonry");
    }

    private List<String> componentNames(JsonNode spec) {
        List<String> names = new ArrayList<>();
        for (JsonNode section : spec.path("sections")) {
            names.add(section.path("component").asText());
        }
        return names;
    }

    private JsonNode findSection(JsonNode sections, String component) {
        for (JsonNode section : sections) {
            if (component.equals(section.path("component").asText())) {
                return section;
            }
        }
        throw new AssertionError("section not found: " + component);
    }
}
