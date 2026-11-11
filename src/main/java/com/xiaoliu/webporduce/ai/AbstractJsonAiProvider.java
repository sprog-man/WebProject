package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.xiaoliu.webporduce.project.PageSpecValidator;

/**
 * 把“生成完整 PageSpec”和“返回局部 JSON Patch”的公共逻辑收敛在一处，
 * 具体供应商只需要实现一次模型 HTTP 调用。
 */
abstract class AbstractJsonAiProvider implements AiProvider {

    private static final String CONNECTION_PROBE_PROMPT =
            "这是一次连接测试。请只返回 JSON 对象 {\"ok\":true}，不要返回其他内容。";

    protected final AiRuntimeConfig config;
    protected final ObjectMapper objectMapper;
    protected final PageSpecValidator specValidator;
    protected final SpecPatchApplier patchApplier;
    protected final String systemPrompt;

    protected AbstractJsonAiProvider(AiRuntimeConfig config,
                                     ObjectMapper objectMapper,
                                     PageSpecValidator specValidator,
                                     SpecPatchApplier patchApplier,
                                     String systemPrompt) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.specValidator = specValidator;
        this.patchApplier = patchApplier;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public boolean available() {
        return !config.baseUrl().isBlank() && !config.model().isBlank();
    }

    @Override
    public AiResult generate(String projectName, JsonNode currentSpec, String message) {
        String prompt = """
                【模式】生成
                【项目名称】%s
                【用户需求】%s

                请返回 {"reply": "...", "pageSpec": {...}}。
                """.formatted(projectName, message);
        JsonNode response = callModel(prompt, 0.7);
        JsonNode spec = response.get("pageSpec");
        if (spec == null || !spec.isObject()) {
            throw new AiProviderException("模型没有返回 pageSpec");
        }
        specValidator.validate(spec);
        ObjectNode meta = (ObjectNode) spec.path("meta");
        if (meta.path("title").asText("").isBlank()) {
            meta.put("title", projectName);
        }
        if (!spec.has("fieldMeta")) {
            ((ObjectNode) spec).putObject("fieldMeta");
        }
        return new AiResult(
                response.path("reply").asText("页面已经生成，可以直接点选修改。"),
                spec,
                true,
                name()
        );
    }

    @Override
    public AiResult edit(JsonNode currentSpec, String message) {
        String specJson;
        try {
            specJson = objectMapper.writeValueAsString(currentSpec);
        } catch (Exception exception) {
            throw new AiProviderException("无法读取当前页面数据", exception);
        }
        String prompt = """
                【模式】局部编辑
                【当前 PageSpec】
                %s

                【用户修改要求】
                %s

                请只返回 {"reply": "...", "patches": [...]}，不要返回完整页面。
                """.formatted(specJson, message);
        JsonNode response = callModel(prompt, 0.35);
        JsonNode patches = response.get("patches");
        JsonNode nextSpec = patchApplier.apply(currentSpec, patches);
        specValidator.validate(nextSpec);
        return new AiResult(
                response.path("reply").asText("已经按你的要求更新了画布。"),
                nextSpec,
                !nextSpec.equals(currentSpec),
                name()
        );
    }

    @Override
    public void testConnection() {
        JsonNode response = callModel(CONNECTION_PROBE_PROMPT, 0.0);
        if (response == null || response.isMissingNode()) {
            throw new AiProviderException("模型没有返回有效响应");
        }
    }

    /**
     * 供应商实现一次最小 JSON 输出请求，返回解析后的 JSON 对象。
     */
    protected abstract JsonNode callModel(String userPrompt, double temperature);

    protected JsonNode parseJsonObject(String content) {
        String cleaned = content == null ? "" : content.trim();
        if (cleaned.isBlank()) {
            throw new AiProviderException("模型返回了空内容");
        }
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception firstFailure) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readTree(cleaned.substring(start, end + 1));
                } catch (Exception ignored) {
                    // fall through
                }
            }
            throw new AiProviderException("模型返回的 JSON 无法解析", firstFailure);
        }
    }

    protected String requestFailureMessage(int statusCode, String body) {
        String detail = body == null ? "" : body.strip();
        if (detail.length() > 240) {
            detail = detail.substring(0, 240) + "…";
        }
        String suffix = detail.isBlank() ? "" : "：" + detail;
        return "模型服务返回错误 " + statusCode + suffix;
    }
}
