package com.xiaomi.sdk.token;

import com.xiaomi.sdk.model.LoginResult;

/**
 * 测试用 TokenManager 桩实现，所有操作仅在内存中完成
 * @author awen
 */
public class StubTokenManager extends TokenManager {

    private LoginResult stored;

    public StubTokenManager() {
        super(null, null, null, null, null);
    }

    @Override
    public void saveToken(LoginResult token, String action, String detail) {
        this.stored = token;
    }

    @Override
    public LoginResult loadActiveToken() {
        return stored;
    }

    @Override
    public LoginResult loadToken(String userId) {
        return stored;
    }

    @Override
    public void removeToken(String userId, String detail) {
        this.stored = null;
    }

    @Override
    public boolean isExpiringSoon(LoginResult token) {
        return false;
    }
}
