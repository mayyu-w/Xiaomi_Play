package com.xiaomi.sdk.mapper;

import com.mybatisflex.core.BaseMapper;
import com.xiaomi.sdk.entity.PlayStateEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 播放状态 Mapper（单记录表）
 * @author awen
 */
@Mapper
public interface PlayStateMapper extends BaseMapper<PlayStateEntity> {
}
