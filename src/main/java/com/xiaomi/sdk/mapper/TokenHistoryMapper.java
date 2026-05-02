package com.xiaomi.sdk.mapper;

import com.mybatisflex.core.BaseMapper;
import com.xiaomi.sdk.entity.TokenHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Token 变更审计 Mapper
 * @author awen
 */
@Mapper
public interface TokenHistoryMapper extends BaseMapper<TokenHistoryEntity> {
}
