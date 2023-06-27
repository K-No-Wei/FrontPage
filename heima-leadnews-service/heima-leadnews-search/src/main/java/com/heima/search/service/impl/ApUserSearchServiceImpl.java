package com.heima.search.service.impl;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.search.dtos.HistorySearchDto;
import com.heima.model.search.pojos.ApUserSearch;
import com.heima.model.user.pojos.ApUser;
import com.heima.search.service.ApUserSearchService;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class ApUserSearchServiceImpl implements ApUserSearchService {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 保存用户搜索历史记录
     *
     * @param keyword
     * @param userId
     */
    @Async
    @Override
    public void insert(String keyword, Integer userId) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("keyword").is(keyword));
        ApUserSearch apUserSearch = mongoTemplate.findOne(query, ApUserSearch.class);

        //存在
        if (apUserSearch != null) {
            apUserSearch.setCreatedTime(new Date());
            mongoTemplate.save(apUserSearch);
            return;
        }

        //不存在，插入
        apUserSearch = new ApUserSearch();
        apUserSearch.setUserId(userId);
        apUserSearch.setKeyword(keyword);
        apUserSearch.setCreatedTime(new Date());

        Query query1 = Query.query(Criteria.where("userId").is(userId));
        query1.with(Sort.by(Sort.Direction.DESC, "createTime"));

        List<ApUserSearch> apUserSearches = mongoTemplate.find(query1, ApUserSearch.class);
        if (apUserSearches == null || apUserSearches.size() < 10) {
            mongoTemplate.save(apUserSearch);
        } else {
            ApUserSearch lastUserSearch = apUserSearches.get(apUserSearches.size() - 1);
            mongoTemplate.findAndReplace(Query.query(Criteria.where("id").is(lastUserSearch.getId())), apUserSearch);
        }
    }

    /**
     * 查询搜索历史
     *
     * @return
     */
    @Override
    public ResponseResult findUserSearch() {
        ApUser user = AppThreadLocalUtil.getUser();
        if (user == null ){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }

        List<ApUserSearch> apUserSearches = mongoTemplate.find(Query.query(Criteria.where("userId").is(user.getId())).with(Sort.by(Sort.Direction.DESC, "createTime")), ApUserSearch.class);

        return ResponseResult.okResult(apUserSearches);
    }

    /**
     * 删除历史记录
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult delUserSearch(HistorySearchDto dto) {
        //1.检查参数
        if(dto.getId() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        //2.判断是否登录
        ApUser user = AppThreadLocalUtil.getUser();
        if(user == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }

        //3.删除
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(user.getId()).and("id").is(dto.getId())),ApUserSearch.class);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
