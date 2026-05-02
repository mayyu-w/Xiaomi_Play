package com.xiaomi.sdk.mapper;

import com.mybatisflex.core.BaseMapper;
import com.xiaomi.sdk.entity.ScheduledTaskLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务执行日志 Mapper
 * @author awen
 */
@Mapper
public interface ScheduledTaskLogMapper extends BaseMapper<ScheduledTaskLogEntity> {
}
