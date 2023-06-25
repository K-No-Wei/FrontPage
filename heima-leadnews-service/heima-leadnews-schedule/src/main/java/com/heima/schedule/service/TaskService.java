package com.heima.schedule.service;

import com.heima.model.schedule.dtos.Task;

public interface TaskService {

    /**
     * 添加延时任务
     * @param task
     * @return
     */
    long addTask(Task task);

    /**
     * 退出任务
     * @param taskId
     * @return
     */
    boolean cancel(long taskId);

    /**
     * 按照类型和优先级来拉取任务
     * @param type
     * @param priority
     * @return
     */
    Task poll(int type,int priority);
}
