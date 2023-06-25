package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.common.redis.CacheService;
import com.heima.model.common.constants.ScheduleConstants;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
@Transactional
@Slf4j
public class TaskServiceImpl implements TaskService {

    @Scheduled(cron = "0 */1 * * * ?")
    public void refresh() {

        String token = cacheService.tryLock("FUTURE_TASK_SYNC", 1000 * 30);
        if (StringUtils.isNotBlank(token)) {
            log.info("定时任务开始");
            Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
            for (String futureKey : futureKeys) {
                String topicKey = ScheduleConstants.TOPIC + futureKey.split(ScheduleConstants.FUTURE)[1];
                Set<String> tasks = cacheService.zRangeByScore(futureKey, 0, System.currentTimeMillis());
                if (!tasks.isEmpty()) {
                    cacheService.refreshWithPipeline(futureKey, topicKey, tasks);
                    log.info("成功的将" + futureKey + "下的当前需要执行的任务数据刷新到" + topicKey + "下");
                }
            }
        }
    }

    /**
     * 添加延时任务
     * @param task
     * @return
     */
    @Override
    public long addTask(Task task) {

        //1.添加任务数据库
        boolean success = addTaskToDB(task);

        //2.添加到redis
        if (success) {
            addTaskToCache(task);
        }

        return 0;
    }

    /**
     * 退出任务
     *
     * @param taskId
     * @return
     */
    @Override
    public boolean cancel(long taskId) {
        //1.删除数据库
        boolean flag = false;

        Task task = updateDB(taskId, ScheduleConstants.CANCELLED);

        //2.删除redis
        if (task != null) {
            removeTaskFormCache(task);
            flag = true;
        }
        return flag;
    }

    /**
     * 按照类型和优先级来拉取任务
     *
     * @param type
     * @param priority
     * @return
     */
    @Override
    public Task poll(int type, int priority) {
        Task task = null;
        try {
            String key = type + "_" + priority;
            String task_json = cacheService.lRightPop(ScheduleConstants.TOPIC + key);
            if (StringUtils.isNotBlank(task_json)) {
                task = JSON.parseObject(task_json, Task.class);
                updateDB(task.getTaskId(), ScheduleConstants.EXECUTED);
            }

        } catch (Exception e) {
            e.printStackTrace();
            log.error("poll task exception");
        }
        return task;
    }

    /**
     * 删除redis的数据
     * @param task
     */
    private void removeTaskFormCache(Task task) {
        String key = task.getTaskType() + "_" + task.getPriority();

        if(task.getExecuteTime()<=System.currentTimeMillis()){
            cacheService.lRemove(ScheduleConstants.TOPIC + key,0, JSON.toJSONString(task));
        }else {
            cacheService.zRemove(ScheduleConstants.FUTURE+key, JSON.toJSONString(task));
        }
    }

    /**
     * 删除任务，更新任务日志状态
     * @param taskId
     * @param status
     * @return
     */
    private Task updateDB(long taskId, int status) {
        Task task = null;
        try {
            taskinfoMapper.deleteById(taskId);

            TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
            taskinfoLogs.setStatus(status);
            taskinfoLogsMapper.updateById(taskinfoLogs);

            task = new Task();
            BeanUtils.copyProperties(taskinfoLogs, task);
            task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());
        } catch (BeansException e) {
            log.error("task cancel exception taskid={}",taskId);
        }
        return task;
    }


    @Autowired
    private CacheService cacheService;

    private void addTaskToCache(Task task) {

        String key = task.getTaskType() + "_" + task.getPriority();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        long nextScheduleTime = calendar.getTimeInMillis();


        if (task.getExecuteTime() <= System.currentTimeMillis()) {
            cacheService.lLeftPush( ScheduleConstants.TOPIC + key, JSON.toJSONString(task));
        } else if (task.getExecuteTime() <= nextScheduleTime) {
            cacheService.zAdd(ScheduleConstants.FUTURE + key, JSON.toJSONString(task), task.getExecuteTime());
        }
    }

    @Autowired
    private TaskinfoLogsMapper taskinfoLogsMapper;

    @Autowired
    private TaskinfoMapper taskinfoMapper;

    /**
     * 数据库添加
     * @param task
     * @return
     */
    private boolean addTaskToDB(Task task) {

        boolean flag = false;

        try {
            Taskinfo taskinfo = new Taskinfo();
            BeanUtils.copyProperties(task, taskinfo);
            taskinfo.setExecuteTime(new Date(task.getExecuteTime()));
            taskinfoMapper.insert(taskinfo);

            TaskinfoLogs taskinfoLogs = new TaskinfoLogs();
            BeanUtils.copyProperties(taskinfo, taskinfoLogs);
            taskinfoLogs.setVersion(1);
            taskinfoLogs.setStatus(ScheduleConstants.SCHEDULED);
            taskinfoLogsMapper.insert(taskinfoLogs);

            flag = true;
            task.setTaskId(taskinfo.getTaskId());
        } catch (BeansException e) {
            throw new RuntimeException(e);
        }

        return flag;
    }

    @Scheduled(cron = "0 */5 * * * ?")
    @PostConstruct
    public void reloadData() {
        clearCache();
        log.info("数据库数据同步到缓存");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);

        //查看小于未来5分钟的所有任务
        List<Taskinfo> allTasks = taskinfoMapper.selectList(Wrappers.<Taskinfo>lambdaQuery().lt(Taskinfo::getExecuteTime,calendar.getTime()));
        if(allTasks != null && allTasks.size() > 0){
            for (Taskinfo taskinfo : allTasks) {
                Task task = new Task();
                BeanUtils.copyProperties(taskinfo,task);
                task.setExecuteTime(taskinfo.getExecuteTime().getTime());
                addTaskToCache(task);
            }
        }
    }

    private void clearCache(){
        // 删除缓存中未来数据集合和当前消费者队列的所有key
        Set<String> futurekeys = cacheService.scan(ScheduleConstants.FUTURE + "*");// future_
        Set<String> topickeys = cacheService.scan(ScheduleConstants.TOPIC + "*");// topic_
        cacheService.delete(futurekeys);
        cacheService.delete(topickeys);
    }
}
