package com.heima.article.job;

import com.heima.article.service.HotArticleService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ComputeHotArticleJob {
    @Autowired
    private HotArticleService articleService;

    @XxlJob("computeHotArticleJob")
    public void handle() {
        log.info("热点文章开始执行...");
        articleService.computeHotArticle();
        log.info("热点文章分值计算任务结束...");
    }
}
