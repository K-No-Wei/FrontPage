package com.heima.article.service.Impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.common.constants.ArticleConstants;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.search.vos.SearchArticleVo;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ArticleFreemarkerServiceImpl implements ArticleFreemarkerService {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private Configuration configuration;

    @Autowired
    private ApArticleService apArticleService;

    /**
     * 生成静态文件上传到minIO中
     *
     * @param apArticle
     * @param content
     */
    @Async
    @Override
    public void buildArticleToMiIO(ApArticle apArticle, String content) {
        if (StringUtils.isNotBlank(content)) {
            Template template = null;
            StringWriter out = new StringWriter();

            try {
                template = configuration.getTemplate("article.ftl");
                Map<String, Object> contentDataModel = new HashMap<>();
                contentDataModel.put("content", JSONArray.parseArray(content));

                template.process(contentDataModel, out);
            } catch (IOException | TemplateException e) {
                throw new RuntimeException(e);
            }

            //4.3 把html文件上传到minio中
            InputStream in = new ByteArrayInputStream(out.toString().getBytes());
            String path = fileStorageService.uploadHtmlFile("", apArticle.getId() + ".html", in);


            //4.4 修改ap_article表，保存static_url字段
            apArticleService.update(Wrappers.<ApArticle>lambdaUpdate().eq(ApArticle::getId,apArticle.getId())
                    .set(ApArticle::getStaticUrl,path));

            //发送消息，创建索引
            createArticleESIndex(apArticle, content, path);

        }
    }

    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    /**
     * kafka消息創建索引
     * @param apArticle
     * @param content
     * @param path
     */
    private void createArticleESIndex(ApArticle apArticle, String content, String path) {
        SearchArticleVo searchArticleVo = new SearchArticleVo();
        BeanUtils.copyProperties(apArticle, searchArticleVo);

        searchArticleVo.setContent(content);
        searchArticleVo.setStaticUrl(path);

        kafkaTemplate.send(ArticleConstants.ARTICLE_ES_SYNC_TOPIC, JSON.toJSONString(searchArticleVo));

    }
}
