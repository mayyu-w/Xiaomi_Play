package com.xiaomi.sdk.mapper;

import com.mybatisflex.core.BaseMapper;
import com.xiaomi.sdk.entity.ScheduledTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务 Mapper
 * @author awen
 */
@Mapper
public interface ScheduledTaskMapper extends BaseMapper<ScheduledTaskEntity> {
}
