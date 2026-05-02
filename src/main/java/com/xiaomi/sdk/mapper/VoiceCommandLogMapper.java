package com.xiaomi.sdk.mapper;

import com.mybatisflex.core.BaseMapper;
import com.xiaomi.sdk.entity.VoiceCommandLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 语音命令执行日志 Mapper
 * @author awen
 */
@Mapper
public interface VoiceCommandLogMapper extends BaseMapper<VoiceCommandLogEntity> {
}
