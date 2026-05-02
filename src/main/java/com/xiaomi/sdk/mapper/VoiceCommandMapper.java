package com.xiaomi.sdk.mapper;

import com.mybatisflex.core.BaseMapper;
import com.xiaomi.sdk.entity.VoiceCommandEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 语音命令关键词配置 Mapper
 * @author awen
 */
@Mapper
public interface VoiceCommandMapper extends BaseMapper<VoiceCommandEntity> {
}
