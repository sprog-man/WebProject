package com.xiaoliu.webporduce.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 应用重启后，内存中的生成任务已经丢失。
 * 把上一次进程遗留的排队中 / 生成中任务标记为失败，避免前端永远显示“生成中”。
 * 页面图片保持不变，用户可以重新发起生成。
 */
@Component
public class ImageJobRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImageJobRecovery.class);

    private final ImageJobRepository jobRepository;

    public ImageJobRecovery(ImageJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<ImageJob> stale = jobRepository.findByStatusIn(
                List.of(ImageJobStatus.PENDING, ImageJobStatus.RUNNING)
        );
        if (stale.isEmpty()) {
            return;
        }
        for (ImageJob job : stale) {
            job.markFailed("服务重启导致任务中断，页面保留了原图片，请重新生成。");
        }
        jobRepository.saveAll(stale);
        log.info("Marked {} interrupted image job(s) as failed after startup", stale.size());
    }
}
