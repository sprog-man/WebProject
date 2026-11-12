package com.xiaoliu.webporduce.image;

public interface ImageProvider {

    ImageGenerationResult generate(ImageGenerationRequest request);

    /**
     * 连接测试会真实生成一张图片，返回图片数据供前端预览。
     * 调用方负责决定是否把这张图片保存为资产。
     */
    default ImageGenerationResult testConnection() {
        return generate(new ImageGenerationRequest(
                "A simple abstract color field with soft natural light, no text, no logo.",
                ImageSizes.DEFAULT,
                null
        ));
    }
}
