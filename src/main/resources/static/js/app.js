// === API 服务 ===
const api = {
    async request(url, options = {}) {
        const resp = await fetch(url, {
            headers: { 'Content-Type': 'application/json' },
            ...options,
        });
        return resp.json();
    },
    get(url) { return this.request(url); },
    post(url, data) { return this.request(url, { method: 'POST', body: JSON.stringify(data) }); },
    put(url, data) { return this.request(url, { method: 'PUT', body: JSON.stringify(data) }); },

    auth: {
        login: (username, password) => api.post('/api/auth/login', { username, password }),
        logout: () => api.post('/api/auth/logout'),
        status: () => api.get('/api/auth/status'),
        qrcode: () => api.get('/api/auth/qrcode'),
        qrcodeStatus: (sessionId) => api.get('/api/auth/qrcode/status?sessionId=' + encodeURIComponent(sessionId)),
    },
    devices: {
        list: () => api.get('/api/devices'),
    },
    music: {
        play: (deviceId, url, direct = false) => api.post('/api/music/play', { deviceId, url, direct }),
        pause: (deviceId) => api.post('/api/music/pause', { deviceId }),
        resume: (deviceId) => api.post('/api/music/resume', { deviceId }),
        next: (deviceId) => api.post('/api/music/next', { deviceId }),
        prev: (deviceId) => api.post('/api/music/prev', { deviceId }),
        volume: (deviceId, volume) => api.post('/api/music/volume', { deviceId, volume }),
        mode: (deviceId, mode) => api.post('/api/music/mode', { deviceId, mode }),
        disableAutoPlay: () => api.post('/api/music/autoplay/disable'),
        status: (deviceId, did, force) => api.get('/api/music/status?deviceId=' + encodeURIComponent(deviceId) + '&did=' + encodeURIComponent(did) + (force ? '&force=true' : '')),
        statusStream: (deviceId) => '/api/music/status/stream?deviceId=' + encodeURIComponent(deviceId),
        setInterval: (deviceId, interval) => api.post('/api/music/status/interval', { deviceId, interval }),
    },
    tts: {
        speak: (deviceId, text) => api.post('/api/tts', { deviceId, text }),
    },
    folder: {
        config: () => api.get('/api/folder/config'),
        saveConfig: (data) => api.post('/api/folder/config', data),
        checkPath: (path) => api.get('/api/folder/check?path=' + encodeURIComponent(path)),
        scan: (path) => api.get('/api/folder/scan' + (path ? '?path=' + encodeURIComponent(path) : '')),
        files: (path) => api.get('/api/folder/files?path=' + encodeURIComponent(path)),
        recordHistory: (data) => api.post('/api/folder/history', data),
        history: () => api.get('/api/folder/history'),
    },
    voice: {
        send: (did, text, speak = false) => api.post('/api/voice/send', { did, text, speak }),
        startPolling: (deviceId, interval = 1) => api.post('/api/voice/polling/start', { deviceId, interval }),
        stopPolling: () => api.post('/api/voice/polling/stop'),
        pollingStatus: () => api.get('/api/voice/polling/status'),
        streamUrl: () => '/api/voice/stream',
        keywords: () => api.get('/api/voice/keywords'),
        addKeyword: (keyword, command) => api.post('/api/voice/keywords', { keyword, command, enabled: true, sortOrder: 0 }),
        updateKeyword: (id, keyword, command, enabled) => api.put('/api/voice/keywords/' + id, { keyword, command, enabled }),
        deleteKeyword: (id) => api.request('/api/voice/keywords/' + id, { method: 'DELETE' }),
    },
    schedule: {
        commands: () => api.get('/api/schedule/commands'),
        playModes: () => api.get('/api/schedule/play-modes'),
        tasks: () => api.get('/api/schedule/tasks'),
        addTask: (data) => api.post('/api/schedule/tasks', data),
        updateTask: (id, data) => api.put('/api/schedule/tasks/' + id, data),
        deleteTask: (id) => api.request('/api/schedule/tasks/' + id, { method: 'DELETE' }),
        validateCron: (expr) => api.get('/api/schedule/cron/validate?expr=' + encodeURIComponent(expr)),
        logs: (taskId, page = 1, size = 10, totalRow = -1) => api.get('/api/schedule/logs?page=' + page + '&size=' + size + '&totalRow=' + totalRow + (taskId ? '&taskId=' + taskId : '')),
        clearLogs: () => api.request('/api/schedule/logs', { method: 'DELETE' }),
    },
};

// === 登录页组件 ===
const LoginPage = {
    template: `
    <div class="login-page">
        <div class="login-card">
            <div class="login-logo">
                <svg viewBox="0 0 48 48" width="40" height="40">
                    <rect width="48" height="48" rx="12" fill="#FF6900"/>
                    <rect x="10" y="10" width="12" height="28" rx="2" fill="#fff"/>
                    <rect x="26" y="10" width="12" height="14" rx="2" fill="#fff"/>
                    <rect x="26" y="28" width="12" height="10" rx="2" fill="#fff"/>
                </svg>
                <h2>小米音箱控制台</h2>
            </div>

            <a-tabs v-model:activeKey="loginMode" centered @change="handleTabChange">
                <a-tab-pane key="password" tab="密码登录">
                    <a-form layout="vertical" @submit.prevent="handleLogin" style="margin-top: 16px">
                        <a-form-item>
                            <a-input v-model:value="form.username" size="large" placeholder="邮箱/手机号码/小米ID"
                                @focus="error = ''">
                                <template #prefix>
                                    <span class="input-icon">👤</span>
                                </template>
                            </a-input>
                        </a-form-item>
                        <a-form-item>
                            <a-input-password v-model:value="form.password" size="large" placeholder="密码"
                                @focus="error = ''">
                                <template #prefix>
                                    <span class="input-icon">🔒</span>
                                </template>
                            </a-input-password>
                        </a-form-item>
                        <div v-if="error" class="login-error">{{ error }}</div>
                        <a-form-item>
                            <a-button type="primary" size="large" block @click="handleLogin"
                                :loading="loading" :disabled="!canSubmit">
                                登录
                            </a-button>
                        </a-form-item>
                    </a-form>
                </a-tab-pane>

                <a-tab-pane key="qrcode" tab="扫码登录">
                    <div class="qrcode-section">
                        <a-spin :spinning="qrLoading" tip="加载中...">
                            <div class="qrcode-image-wrapper">
                                <img v-if="qrImageUrl" :src="qrImageUrl" alt="扫码登录"
                                     class="qrcode-image" @error="onQrImageError" />
                                <div v-if="qrStatus === 'expired'" class="qrcode-overlay"
                                     @click="refreshQrCode">
                                    <div class="qrcode-overlay-text">二维码已过期<br>点击刷新</div>
                                </div>
                                <div v-if="qrStatus === 'error'" class="qrcode-overlay"
                                     @click="refreshQrCode">
                                    <div class="qrcode-overlay-text">加载失败<br>点击重试</div>
                                </div>
                            </div>
                        </a-spin>
                        <p class="qrcode-hint">
                            <span v-if="qrStatus === 'waiting'">请使用小米手机或米家 App 扫码登录</span>
                            <span v-else-if="qrStatus === 'confirmed'" class="text-success">登录成功，正在跳转...</span>
                            <span v-else-if="qrStatus === 'expired'" class="text-error">二维码已过期，请点击刷新</span>
                            <span v-else-if="qrStatus === 'error'" class="text-error">{{ qrMessage || '加载失败' }}</span>
                            <span v-else>请使用小米手机或米家 App 扫码登录</span>
                        </p>
                    </div>
                </a-tab-pane>
            </a-tabs>
        </div>
    </div>
    `,
    data() {
        return {
            loginMode: 'password',
            form: { username: '', password: '' },
            loading: false,
            error: '',
            qrLoading: false,
            qrImageUrl: '',
            qrSessionId: '',
            qrStatus: '',
            qrMessage: '',
            qrPollTimer: null,
        };
    },
    computed: {
        canSubmit() {
            return this.form.username && this.form.password && !this.loading;
        },
    },
    methods: {
        async handleLogin() {
            if (!this.canSubmit) return;
            this.loading = true;
            this.error = '';
            try {
                const res = await api.auth.login(this.form.username, this.form.password);
                if (res.success) {
                    if (res.data.username) {
                        localStorage.setItem('xm_username', res.data.username);
                    }
                    this.$router.push('/devices');
                } else {
                    this.error = res.message || '登录失败';
                }
            } catch (e) {
                this.error = '网络错误，请稍后重试';
            } finally {
                this.loading = false;
            }
        },
        handleTabChange(key) {
            if (key === 'qrcode') {
                this.loadQrCode();
            } else {
                this.stopQrPolling();
            }
        },
        async loadQrCode() {
            this.qrLoading = true;
            this.qrStatus = '';
            this.qrMessage = '';
            this.qrImageUrl = '';
            try {
                const res = await api.auth.qrcode();
                if (res.success && res.data) {
                    this.qrImageUrl = res.data.qrImageUrl;
                    this.qrSessionId = res.data.sessionId;
                    this.qrStatus = 'waiting';
                    this.startQrPolling();
                } else {
                    this.qrStatus = 'error';
                    this.qrMessage = res.message || '获取二维码失败';
                }
            } catch (e) {
                this.qrStatus = 'error';
                this.qrMessage = '网络错误，请稍后重试';
            } finally {
                this.qrLoading = false;
            }
        },
        refreshQrCode() {
            this.stopQrPolling();
            this.loadQrCode();
        },
        startQrPolling() {
            this.stopQrPolling();
            this.qrPollTimer = setInterval(() => this.pollQrStatus(), 3000);
        },
        stopQrPolling() {
            if (this.qrPollTimer) {
                clearInterval(this.qrPollTimer);
                this.qrPollTimer = null;
            }
        },
        async pollQrStatus() {
            if (!this.qrSessionId) return;
            try {
                const res = await api.auth.qrcodeStatus(this.qrSessionId);
                if (res.success && res.data) {
                    this.qrStatus = res.data.status;
                    this.qrMessage = res.data.message || '';
                    if (this.qrStatus === 'confirmed') {
                        this.stopQrPolling();
                        setTimeout(() => this.$router.push('/devices'), 500);
                    } else if (this.qrStatus === 'expired' || this.qrStatus === 'error') {
                        this.stopQrPolling();
                    }
                }
            } catch {}
        },
        onQrImageError() {
            this.qrStatus = 'error';
            this.qrMessage = '二维码图片加载失败';
        },
    },
    async mounted() {
        try {
            const res = await api.auth.status();
            if (res.success && res.data.loggedIn) {
                this.$router.replace('/devices');
            }
        } catch {}
    },
    beforeUnmount() {
        this.stopQrPolling();
    },
};

// === 设备列表页组件 ===
const DevicesPage = {
    template: `
    <div class="app-layout">
        <header class="app-header">
            <div class="app-header-title">
                <span class="logo-dot"></span>
                小米音箱控制台
            </div>
            <a-space>
                <span class="text-secondary">{{ displayName }}</span>
                <span class="text-secondary" style="opacity:0.6">ID: {{ userId }}</span>
                <a-button size="small" @click="handleLogout">退出</a-button>
            </a-space>
        </header>
        <main class="app-content">
            <div class="devices-header">
                <h2>我的设备</h2>
                <a-space>
                    <a-button @click="$router.push('/schedule')">定时任务</a-button>
                    <a-button @click="$router.push('/voice')">语音命令</a-button>
                    <a-button @click="loadDevices" :loading="loading">刷新</a-button>
                </a-space>
            </div>

            <a-spin :spinning="loading">
                <div v-if="devices.length === 0 && !loading" class="empty-state">
                    <div class="empty-state-icon">📡</div>
                    <div>未发现任何设备</div>
                </div>
                <div v-else class="device-grid">
                    <div v-for="device in devices" :key="device.did || device.deviceId"
                         class="device-card" @click="goControl(device)">
                        <div class="device-card-name">{{ device.name || '未命名设备' }}</div>
                        <div class="device-card-model">{{ device.model }}</div>
                        <div class="device-card-did">DID: {{ device.did || device.deviceId }}</div>
                    </div>
                </div>
            </a-spin>
        </main>
    </div>
    `,
    data() {
        return { devices: [], loading: false, userId: '', displayName: '' };
    },
    methods: {
        async loadDevices() {
            this.loading = true;
            try {
                const res = await api.devices.list();
                if (res.success) {
                    this.devices = res.data || [];
                } else {
                    this.$message.error(res.message);
                    if (res.message && res.message.includes('登录')) {
                        this.$router.replace('/login');
                    }
                }
            } catch (e) {
                this.$message.error('网络错误');
            } finally {
                this.loading = false;
            }
        },
        goControl(device) {
            this.$router.push({
                path: '/control',
                query: {
                    deviceId: device.deviceId || device.did,
                    did: device.did,
                    name: device.name || '未命名设备',
                    model: device.model,
                },
            });
        },
        async handleLogout() {
            await api.auth.logout();
            localStorage.removeItem('xm_username');
            this.$router.replace('/login');
        },
    },
    async mounted() {
        const res = await api.auth.status();
        if (!res.success || !res.data.loggedIn) {
            this.$router.replace('/login');
            return;
        }
        this.userId = res.data.userId || '';
        const nickname = res.data.nickname;
        if (nickname) {
            localStorage.setItem('xm_username', nickname);
            this.displayName = nickname;
        } else {
            const username = localStorage.getItem('xm_username');
            this.displayName = username || ('用户 ' + this.userId);
        }
        await this.loadDevices();
    },
};

// === 设备控制页组件 ===
const ControlPage = {
    template: `
    <div class="app-layout">
        <header class="app-header">
            <div class="app-header-title">
                <span class="logo-dot"></span>
                小米音箱控制台
            </div>
            <a-button size="small" @click="$router.push('/devices')">返回设备列表</a-button>
        </header>
        <main class="app-content">
            <div class="control-header">
                <h2>{{ deviceName }}</h2>
                <a-tag v-if="deviceModel">{{ deviceModel }}</a-tag>
            </div>

            <!-- 音乐控制 -->
            <div class="control-section">
                <div class="control-section-title">🎵 音乐控制</div>
                <div class="playlist-section mb-16">
                    <div class="playlist-row">
                        <a-select v-model:value="selectedFolder" style="flex:1"
                            placeholder="选择播放列表" :disabled="folderList.length === 0"
                            @change="handleFolderSelect">
                            <a-select-option v-for="f in folderList" :key="f.path" :value="f.path">
                                {{ f.name }}（{{ f.count }}）
                            </a-select-option>
                        </a-select>
                    </div>
                    <div class="playlist-row mt-8">
                        <a-select v-model:value="selectedFile" style="flex:1"
                            placeholder="选择音频文件" show-search :filter-option="filterFile"
                            :disabled="fileList.length === 0">
                            <a-select-option v-for="f in fileList" :key="f.path" :value="f.path">
                                {{ f.name }}
                            </a-select-option>
                        </a-select>
                        <a-button type="primary" @click="handlePlayFile" :loading="musicLoading"
                            :disabled="!selectedFile">▶ 播放</a-button>
                    </div>
                </div>
                <div class="music-controls">
                    <a-button @click="showPlayMode = true" title="播放模式">
                        {{ {0:'🔁',1:'🔂',2:'🔀',3:'▶',4:'📋'}[playMode] || '▶' }}播放模式
                    </a-button>
                    <a-button @click="handlePrev" :loading="musicLoading">⏮ 上一首</a-button>
                    <a-button @click="handlePause" :loading="musicLoading">⏸ 暂停</a-button>
                    <a-button type="primary" @click="handleResume" :loading="musicLoading">▶ 恢复</a-button>
                    <a-button @click="handleNext" :loading="musicLoading">⏭ 下一首</a-button>
                </div>
                <!-- 播放进度 -->
                <div class="progress-row">
                    <span class="progress-time">{{ formatTime(displayPlayTime) }}</span>
                    <a-slider v-model:value="progressPercent" :min="0" :max="100" :disabled="displayDuration <= 0"
                        :tip-formatter="val => formatTime(displayDuration * val / 100)" />
                    <span class="progress-time">{{ displayDuration > 0 ? formatTime(displayDuration) : '--:--' }}</span>
                </div>
                 <!-- 当前播放 -->
                <div v-if="currentFileName" class="current-playing-label">
                    <span>当前播放：</span>
                    <span v-if="playSource === 'schedule'" class="play-source schedule">☑ 定时任务</span>
                    <span v-else-if="playSource === 'page'" class="play-source page">♪ 本页播放</span>
                    <span class="playing-icon">♪</span> {{ currentFileName }}
                </div>
            </div>

            <!-- 设备状态 -->
            <div class="control-section">
                <div class="control-section-title">📊 设备状态</div>
                <div class="volume-row">
                    <span>音量：</span>
                    <a-slider v-model:value="volume" :min="0" :max="100" @change="handleVolumeChange" />
                    <span class="volume-value">{{ volume }}%</span>
                </div>
                <div class="music-status mt-16">
                    <div class="music-status-item">
                        状态：<span class="music-status-value">{{ {0: '停止', 1: '播放中', 2: '暂停'}[playerStatus.status] || '未知(' + playerStatus.status + ')' }}</span>
                    </div>
                    <div class="music-status-item">
                        模式：<span class="music-status-value">{{ {0: '单曲循环', 1: '全部循环', 2: '随机播放', 3: '单曲播放', 4: '顺序播放'}[playMode] || '未知' }}</span>
                    </div>
                    <div class="music-status-item">
                        音量：<span class="music-status-value">{{ playerStatus.volume }}%</span>
                    </div>
                    <svg class="countdown-ring" :style="{ '--progress': countdownProgress }">
                        <circle class="ring-bg" cx="14" cy="14" r="11" />
                        <circle class="ring-progress" cx="14" cy="14" r="11"
                            :stroke-dasharray="circumference"
                            :stroke-dashoffset="circumference * (1 - countdownProgress)" />
                    </svg>
                </div>
            </div>


            <!-- 播放模式弹窗 -->
            <a-modal v-model:visible="showPlayMode" title="播放模式" :footer="null" width="320px">
                <div class="play-mode-list">
                    <div v-for="m in playModes" :key="m.value"
                         class="play-mode-item" :class="{ active: playMode === m.value }"
                         @click="handleSetPlayMode(m.value)">
                        <span class="play-mode-icon">{{ m.icon }}</span>
                        <span>{{ m.label }}</span>
                    </div>
                </div>
            </a-modal>

            <!-- TTS 语音播报 -->
            <div class="control-section">
                <div class="control-section-title">🔊 语音播报</div>
                <div class="tts-row">
                    <a-input v-model:value="ttsText" placeholder="输入要播报的文本" />
                    <a-button type="primary" @click="handleTts" :loading="ttsLoading">播报</a-button>
                </div>
            </div>

            <!-- 文件夹监控设置 -->
            <div class="control-section">
                <div class="control-section-title">📂 文件夹监控设置</div>
                <div class="folder-config-form">
                    <div class="folder-config-row">
                        <label>路径</label>
                        <a-input v-model:value="folderConfig.path" placeholder="如 /music，Docker 挂载目录" />
                        <a-button size="small" @click="handleCheckPath" :loading="pathChecking">
                            {{ pathValid ? '✓ 有效' : '检测' }}
                        </a-button>
                    </div>
                    <div class="folder-config-row">
                        <label>服务地址</label>
                        <a-input v-model:value="folderConfig.serverUrl"
                            placeholder="音箱可访问的地址，如 http://192.168.1.100:8080" />
                    </div>
                    <div class="folder-config-row">
                        <label>文件监控</label>
                        <a-switch v-model:checked="folderConfig.watchEnabled" checked-children="开" un-checked-children="关" />
                    </div>
                    <div class="folder-config-row">
                        <label>监控间隔</label>
                        <a-select v-model:value="folderConfig.watchInterval" style="width:120px">
                            <a-select-option :value="5">5 秒</a-select-option>
                            <a-select-option :value="10">10 秒</a-select-option>
                            <a-select-option :value="30">30 秒</a-select-option>
                            <a-select-option :value="60">1 分钟</a-select-option>
                            <a-select-option :value="300">5 分钟</a-select-option>
                            <a-select-option :value="600">10 分钟</a-select-option>
                        </a-select>
                    </div>
                    <div class="folder-config-row">
                        <label>忽略目录</label>
                        <a-input v-model:value="folderConfig.ignoreDirs"
                            placeholder="用英文逗号分隔，如 tmp,cache" @blur="fixIgnoreDirs" />
                    </div>
                    <div class="folder-config-row">
                        <label>目录深度</label>
                        <a-input-number v-model:value="folderConfig.maxDepth" :min="1" :max="50" style="width:120px" />
                    </div>
                    <div class="folder-config-row" style="justify-content:flex-end">
                        <a-button type="primary" @click="handleSaveConfig" :loading="configSaving"
                            :disabled="!pathValid">保存配置</a-button>
                    </div>
                </div>
            </div>

        </main>
    </div>
    `,
    data() {
        const query = new URLSearchParams(window.location.search);
        return {
            deviceId: query.get('deviceId') || '',
            did: query.get('did') || '',
            deviceName: query.get('name') || '设备控制',
            deviceModel: query.get('model') || '',
            // 音乐
            volume: 50,
            musicLoading: false,

            playerStatus: { status: 0, volume: 0, mediaType: '', mediaId: '', playTime: 0, duration: 0 },
            // TTS
            ttsText: '',
            ttsLoading: false,
            // 文件夹
            folderConfig: { path: '', watchEnabled: false, watchInterval: 10, ignoreDirs: '', maxDepth: 10, serverUrl: '' },
            configSaving: false,
            pathValid: false,
            pathChecking: false,
            folderList: [],
            selectedFolder: '',
            folderLoading: false,
            fileList: [],
            selectedFile: '',
            // 播放模式
            playMode: 4,
            showPlayMode: false,
            playModes: [
                { value: 0, label: '单曲循环', icon: '🔁' },
                { value: 1, label: '全部循环', icon: '🔂' },
                { value: 2, label: '随机播放', icon: '🔀' },
                { value: 3, label: '单曲播放', icon: '▶' },
                { value: 4, label: '顺序播放', icon: '📋' },
            ],
            // 定时刷新
            refreshInterval: 1, // 硬编码1秒
            // 播放进度
            displayPlayTime: 0,
            displayDuration: 0,
            progressPercent: 0,
            progressTimer: null,
            statusBasePlayTime: 0,
            statusBaseTimestamp: 0,
            progressActive: false,
            // 歌曲结束自动播放
            manualPause: false,
            prevPlayStatus: 0,
            autoPlaying: false,
            frontendControlled: false, // true = 前端接管切歌，后端不切
            backendAutoPlay: false,    // true = 后端正在自动切歌
            backendCurrentFile: null,  // 后端推送的当前播放文件路径
            playStartTime: 0,
            pendingNewSong: false,
            sseSource: null,
            // 歌曲结束定时器（主策略：提前发新URL）
            songEndTimer: null,
            // 保护锁：用户手动操作后短时间内不让定时刷新覆盖
            volumeLockUntil: 0,
            // 倒计时进度圈
            countdownProgress: 1,
            countdownStart: 0,
            countdownRaf: null,

            circumference: 2 * Math.PI * 11,
        };
    },
    computed: {
        currentFileName() {
            if (this.backendAutoPlay && !this.frontendControlled && this.backendCurrentFile) {
                const name = this.backendCurrentFile.includes('/')
                    ? this.backendCurrentFile.substring(this.backendCurrentFile.lastIndexOf('/') + 1)
                    : this.backendCurrentFile;
                return name;
            }
            if (!this.selectedFile) return '';
            const parts = this.selectedFile.replace(/\\/g, '/').split('/');
            return parts[parts.length - 1];
        },
        playSource() {
            if (this.frontendControlled) return 'page';
            if (this.backendAutoPlay) return 'schedule';
            return '';
        },
    },
    methods: {
        getDeviceId() {
            return this.deviceId || this.did;
        },
        async takeControl() {
            if (!this.frontendControlled) {
                this.frontendControlled = true;
                await api.music.disableAutoPlay().catch(() => {});
            }
        },
        async handlePause() {
            await this.takeControl();
            this.manualPause = true;
            this.clearSongEndTimer();
            this.musicLoading = true;
            try {
                const res = await api.music.pause(this.getDeviceId());
                res.success ? this.$message.success('已暂停') : this.$message.error(res.message);
            } catch { this.$message.error('网络错误'); }
            finally { this.musicLoading = false; }
        },
        async handleResume() {
            await this.takeControl();
            this.manualPause = false;
            this.progressActive = true;
            this.musicLoading = true;
            try {
                const res = await api.music.resume(this.getDeviceId());
                res.success ? this.$message.success('恢复播放') : this.$message.error(res.message);
            } catch { this.$message.error('网络错误'); }
            finally { this.musicLoading = false; }
        },
        async handleNext() {
            await this.takeControl();
            const idx = this.fileList.findIndex(f => f.path === this.selectedFile);
            if (idx < 0 || this.fileList.length === 0) {
                this.$message.warning('请先选择音频文件');
                return;
            }
            const nextIdx = (idx + 1) % this.fileList.length;
            const nextFile = this.fileList[nextIdx];
            this.selectedFile = nextFile.path;
            await this.playFile(nextFile);
        },
        async handlePrev() {
            await this.takeControl();
            const idx = this.fileList.findIndex(f => f.path === this.selectedFile);
            if (idx < 0 || this.fileList.length === 0) {
                this.$message.warning('请先选择音频文件');
                return;
            }
            const prevIdx = (idx - 1 + this.fileList.length) % this.fileList.length;
            const prevFile = this.fileList[prevIdx];
            this.selectedFile = prevFile.path;
            await this.playFile(prevFile);
        },
        async playFile(file, direct = false) {
            await this.takeControl();
            this.musicLoading = true;
            this.manualPause = false;
            this.clearSongEndTimer();
            this.progressActive = true;
            this.playStartTime = Date.now();
            this.pendingNewSong = true;
            this.displayPlayTime = 0;
            this.displayDuration = 0;
            this.progressPercent = 0;
            this.statusBasePlayTime = 0;
            this.statusBaseTimestamp = Date.now();
            this.prevPlayStatus = 0;
            this.autoPlaying = false;
            try {
                const audioUrl = this.toAudioUrl(file.urlPath);
                const res = await api.music.play(this.getDeviceId(), audioUrl, direct);
                if (res.success) {
                    this.$message.success('播放：' + file.name);
                    api.folder.recordHistory({
                        folderPath: this.selectedFolder,
                        fileName: file.name,
                        filePath: file.path,
                        urlPath: file.urlPath,
                    });
                } else {
                    this.$message.error(res.message);
                }
            } catch { this.$message.error('网络错误'); }
            finally { this.musicLoading = false; }
        },
        async handleVolumeChange(val) {
            await this.takeControl();
            this.volumeLockUntil = Date.now() + 3000;
            try {
                const res = await api.music.volume(this.getDeviceId(), val);
                if (res.success && res.data && res.data.volume !== undefined) {
                    this.playerStatus = res.data;
                }
            } catch {}
        },
        // ---- SSE 状态接收 ----
        initSSE() {
            this.stopSSE();
            const deviceId = this.getDeviceId();
            if (!deviceId) return;
            const url = api.music.statusStream(deviceId);
            this.sseSource = new EventSource(url);
            this.sseSource.addEventListener('status', (event) => {
                try {
                    const raw = JSON.parse(event.data);
                    const data = raw.playerStatus || raw;
                    data._timestamp = raw.timestamp;
                    data._autoPlay = raw.autoPlay || false;
                    data._currentUrlPath = raw.currentUrlPath || null;
                    this.handleSSEStatus(data);
                } catch (e) { /* ignore parse errors */ }
            });
            this.sseSource.onerror = () => {
                this.stopSSE();
                setTimeout(() => this.initSSE(), 3000);
            };
        },
        stopSSE() {
            if (this.sseSource) {
                this.sseSource.close();
                this.sseSource = null;
            }
        },
        handleSSEStatus(data) {
            if (!data) return;
            this.playerStatus = data;
            const now = Date.now();
            const newStatus = data.status || 0;
            const newPlayTime = data.playTime || 0;
            const newDuration = data.duration || 0;
            const backendAutoPlay = data._autoPlay || false;
            const backendCurrentFile = data._currentUrlPath || null;

            if (now > this.volumeLockUntil) {
                this.volume = data.volume || 0;
            }

            // 后端接管切歌：更新显示，跳过前端切歌逻辑
            this.backendAutoPlay = backendAutoPlay;
            this.backendCurrentFile = backendCurrentFile;
            if (backendAutoPlay && !this.frontendControlled) {
                if (backendCurrentFile && this.fileList.length > 0) {
                    const matched = this.fileList.find(f => f.urlPath === backendCurrentFile);
                    if (matched) this.selectedFile = matched.path;
                }
                this.displayPlayTime = newPlayTime;
                this.displayDuration = newDuration;
                this.syncProgressPercent();
                this.resetCountdown();
                return;
            }

            this.syncCurrentPlaying();

            // 启动防自动播放：未主动播放时仅更新显示，跳过所有检测
            if (!this.progressActive) {
                this.statusBasePlayTime = newPlayTime;
                this.statusBaseTimestamp = now;
                this.displayPlayTime = newPlayTime;
                this.displayDuration = newDuration;
                this.syncProgressPercent();
                this.resetCountdown();
                return;
            }

            // 新歌等待后端确认（status=1 且 duration>0 且 position<5s）
            if (this.pendingNewSong) {
                if (newStatus === 1 && newDuration > 0 && newPlayTime < 5) {
                    this.pendingNewSong = false;
                    this.statusBasePlayTime = newPlayTime;
                    this.statusBaseTimestamp = now;
                    this.displayPlayTime = newPlayTime;
                    this.displayDuration = newDuration;
                    this.syncProgressPercent();
                    this.prevPlayStatus = newStatus;
                    this.setSongEndTimer(newDuration - newPlayTime);
                }
                this.resetCountdown();
                return;
            }
            // 回跳兜底：设备已自动循环（position 从接近结尾跳回接近0）
            if (!this.manualPause && !this.autoPlaying
                && this.selectedFile && newDuration > 0
                && this.statusBasePlayTime > newDuration * 0.8
                && newPlayTime < this.statusBasePlayTime - 5) {
                this.clearSongEndTimer();
                this.progressActive = false;
                this.handleSongEnd();
                this.resetCountdown();
                return;
            }
            // 暂停时清除定时器
            if (newStatus === 2 && this.prevPlayStatus === 1) {
                this.clearSongEndTimer();
            }
            // 恢复播放时重建定时器
            if (newStatus === 1 && this.prevPlayStatus === 2 && newDuration > 0) {
                this.setSongEndTimer(newDuration - newPlayTime);
            }
            // 正常更新跟踪基准
            this.statusBasePlayTime = newPlayTime;
            this.statusBaseTimestamp = now;
            this.displayPlayTime = newPlayTime;
            this.displayDuration = newDuration;
            this.syncProgressPercent();
            this.prevPlayStatus = newStatus;
            this.resetCountdown();
        },
        // 手动强制刷新（保留 HTTP 后备）
        async refreshStatus() {
            try {
                const res = await api.music.status(this.getDeviceId(), this.did, true);
                if (res.success && res.data) {
                    this.handleSSEStatus(res.data);
                }
            } catch {}
        },
        async handleSongEnd() {
            this.clearSongEndTimer();
            this.autoPlaying = true;
            const idx = this.fileList.findIndex(f => f.path === this.selectedFile);
            if (idx < 0 || this.fileList.length === 0) { this.autoPlaying = false; return; }
            switch (this.playMode) {
                case 0: { // 单曲循环 — 重新播放同一首
                    const curFile = this.fileList[idx];
                    await this.playFile(curFile, true);
                    break;
                }
                case 1: { // 全部循环
                    const nextIdx = (idx + 1) % this.fileList.length;
                    const nextFile = this.fileList[nextIdx];
                    this.selectedFile = nextFile.path;
                    await this.playFile(nextFile, true);
                    break;
                }
                case 2: { // 随机播放
                    let rIdx = Math.floor(Math.random() * this.fileList.length);
                    if (this.fileList.length > 1) {
                        while (rIdx === idx) rIdx = Math.floor(Math.random() * this.fileList.length);
                    }
                    const randFile = this.fileList[rIdx];
                    this.selectedFile = randFile.path;
                    await this.playFile(randFile, true);
                    break;
                }
                case 3: // 单曲播放 — 播完停止
                    this.progressActive = false;
                    break;
                case 4: { // 顺序播放 — 播放下一首，到末尾停止
                    if (idx < this.fileList.length - 1) {
                        const nextFile = this.fileList[idx + 1];
                        this.selectedFile = nextFile.path;
                        await this.playFile(nextFile, true);
                    } else {
                        this.progressActive = false;
                    }
                    break;
                }
            }
            this.autoPlaying = false;
        },
        async handleTts() {
            if (!this.ttsText) { this.$message.warning('请输入播报文本'); return; }
            this.ttsLoading = true;
            try {
                const res = await api.tts.speak(this.getDeviceId(), this.ttsText);
                res.success ? this.$message.success('播报成功') : this.$message.error(res.message);
            } catch { this.$message.error('网络错误'); }
            finally { this.ttsLoading = false; }
        },
        async handleScanFolder(path) {
            this.folderLoading = true;
            try {
                const res = await api.folder.scan(path || undefined);
                if (res.success) {
                    this.folderList = res.data || [];
                    if (this.folderList.length > 0) {
                        this.selectedFolder = this.folderList[0].path;
                        await this.handleFolderSelect(this.selectedFolder);
                    } else {
                        this.selectedFolder = '';
                        this.fileList = [];
                    }
                    this.selectedFile = '';
                } else {
                    this.folderList = [];
                    this.selectedFolder = '';
                    this.fileList = [];
                    this.$message.error(res.message);
                }
            } catch { this.$message.error('网络错误'); }
            finally { this.folderLoading = false; }
        },
        async handleFolderSelect(path) {
            this.fileList = [];
            this.selectedFile = '';
            if (!path) return;
            try {
                const res = await api.folder.files(path);
                if (res.success) {
                    this.fileList = res.data || [];
                    this.syncCurrentPlaying();
                }
            } catch {}
        },
        async handlePlayFile() {
            if (!this.selectedFile) { this.$message.warning('请选择音频文件'); return; }
            const file = this.fileList.find(f => f.path === this.selectedFile);
            if (file) {
                await this.playFile(file);
            }
        },
        filterFile(input, option) {
            return option.children[0].children.toLowerCase().includes(input.toLowerCase());
        },
        syncCurrentPlaying() {
            const mediaId = this.playerStatus.mediaId;
            if (!mediaId || this.fileList.length === 0) return;
            const matched = this.fileList.find(f =>
                mediaId.includes(f.name) || mediaId === f.path
            );
            if (matched) {
                this.selectedFile = matched.path;
            }
        },
        async loadFolderConfig() {
            try {
                const res = await api.folder.config();
                if (res.success && res.data) {
                    this.folderConfig = { ...this.folderConfig, ...res.data };
                    if (res.data.playMode !== undefined) {
                        this.playMode = res.data.playMode;
                    }
                    if (res.data.path) {
                        this.pathValid = true;
                        await this.handleScanFolder();
                        await this.restoreHistory();
                    }
                }
            } catch {}
        },
        async restoreHistory() {
            try {
                const res = await api.folder.history();
                if (res.success && res.data) {
                    const h = res.data;
                    if (h.folderPath) {
                        this.selectedFolder = h.folderPath;
                        await this.handleFolderSelect(h.folderPath);
                    }
                    if (h.filePath && this.fileList.some(f => f.path === h.filePath)) {
                        this.selectedFile = h.filePath;
                    }
                }
            } catch {}
        },
        toAudioUrl(urlPath) {
            const base = this.folderConfig.serverUrl || window.location.origin;
            const parts = urlPath.split('/').map(p => encodeURIComponent(p)).join('/');
            return base + '/api/folder/audio/' + parts;
        },
        async handleSaveConfig() {
            if (!this.pathValid) { this.$message.warning('请先检测路径'); return; }
            this.configSaving = true;
            try {
                const res = await api.folder.saveConfig(this.folderConfig);
                if (res.success) {
                    this.$message.success('保存成功');
                    if (this.folderConfig.path) {
                        this.handleScanFolder();
                    }
                } else {
                    this.$message.error(res.message);
                }
            } catch { this.$message.error('网络错误'); }
            finally { this.configSaving = false; }
        },
        async handleCheckPath() {
            if (!this.folderConfig.path) { this.$message.warning('请输入路径'); return; }
            this.pathChecking = true;
            try {
                const res = await api.folder.checkPath(this.folderConfig.path);
                if (res.success) {
                    this.pathValid = true;
                    this.$message.success('目录有效');
                } else {
                    this.pathValid = false;
                    this.$message.error(res.message);
                }
            } catch { this.$message.error('网络错误'); this.pathValid = false; }
            finally { this.pathChecking = false; }
        },
        fixIgnoreDirs() {
            if (this.folderConfig.ignoreDirs) {
                this.folderConfig.ignoreDirs = this.folderConfig.ignoreDirs
                    .replace(/[，、；]/g, ',')
                    .replace(/,\s*/g, ',')
                    .replace(/^,|,$/g, '');
            }
        },
        async handleSetPlayMode(mode) {
            await this.takeControl();
            try {
                const res = await api.music.mode(this.getDeviceId(), mode);
                if (res.success) {
                    this.playMode = mode;
                    this.$message.success('播放模式：' + this.playModes.find(m => m.value === mode).label);
                } else {
                    this.$message.error(res.message || '设置失败');
                }
            } catch { this.$message.error('网络错误'); }
            this.showPlayMode = false;
        },
        // ---- 歌曲结束定时器 ----
        setSongEndTimer(remainingSec) {
            this.clearSongEndTimer();
            if (!this.progressActive || this.manualPause || !this.selectedFile) return;
            const buffer = 1.5; // 提前1.5秒发新URL（设备切换延迟约1.6秒）
            const delay = Math.max(0, (remainingSec - buffer) * 1000);
            if (delay > 0) {
                this.songEndTimer = setTimeout(() => {
                    this.songEndTimer = null;
                    if (this.progressActive && !this.manualPause && !this.autoPlaying) {
                        this.progressActive = false;
                        this.handleSongEnd();
                    }
                }, delay);
            }
        },
        clearSongEndTimer() {
            if (this.songEndTimer) {
                clearTimeout(this.songEndTimer);
                this.songEndTimer = null;
            }
        },
        resetCountdown() {
            this.stopCountdown();
            this.countdownStart = performance.now();
            this.countdownProgress = 1;
            const tick = () => {
                const elapsed = (performance.now() - this.countdownStart) / 1000;
                this.countdownProgress = Math.max(0, 1 - elapsed / this.refreshInterval);
                if (this.countdownProgress > 0) {
                    this.countdownRaf = requestAnimationFrame(tick);
                } else {
                    this.countdownRaf = null;
                }
            };
            tick();
        },
        stopCountdown() {
            if (this.countdownRaf) {
                cancelAnimationFrame(this.countdownRaf);
                this.countdownRaf = null;
            }
        },
        formatTime(seconds) {
            if (!seconds || seconds <= 0) return '0:00';
            const m = Math.floor(seconds / 60);
            const s = Math.floor(seconds % 60);
            return m + ':' + (s < 10 ? '0' : '') + s;
        },
        syncProgressPercent() {
            if (this.displayDuration > 0) {
                this.progressPercent = Math.min(100, (this.displayPlayTime / this.displayDuration) * 100);
            } else {
                this.progressPercent = 0;
            }
        },
        startProgressTimer() {
            this.stopProgressTimer();
            this.progressTimer = setInterval(() => {
                if (this.progressActive && this.displayDuration > 0) {
                    const elapsed = (Date.now() - this.statusBaseTimestamp) / 1000;
                    this.displayPlayTime = this.statusBasePlayTime + elapsed;
                    if (this.displayPlayTime > this.displayDuration) {
                        this.displayPlayTime = this.displayDuration;
                    }
                    this.syncProgressPercent();
                }
            }, 500);
        },
        stopProgressTimer() {
            if (this.progressTimer) {
                clearInterval(this.progressTimer);
                this.progressTimer = null;
            }
        },
    },
    async mounted() {
        api.music.setInterval(this.getDeviceId(), 1);
        this.initSSE();
        this.startProgressTimer();
        this.loadFolderConfig();
    },
    beforeUnmount() {
        this.clearSongEndTimer();
        this.stopSSE();
        this.stopCountdown();
        this.stopProgressTimer();
    },
};

// === 定时任务页组件 ===
const SchedulePage = {
    template: `
    <div class="app-layout">
        <header class="app-header">
            <div class="app-header-title">
                <span class="logo-dot"></span>
                小米音箱控制台
            </div>
            <a-button size="small" @click="$router.push('/devices')">返回设备列表</a-button>
        </header>
        <main class="app-content">
            <div class="control-header">
                <h2>定时任务</h2>
                <a-button type="primary" @click="openAddModal">+ 添加任务</a-button>
            </div>

            <a-table :dataSource="tasks" :columns="taskColumns" :pagination="false" size="small" rowKey="id">
                <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'enabled'">
                        <a-switch :checked="record.enabled" @change="(v) => handleToggle(record, v)" size="small" />
                    </template>
                    <template v-if="column.key === 'cronExpr'">
                        <code style="background:#f5f5f5; padding:2px 6px; border-radius:4px;">{{ record.cronExpr }}</code>
                    </template>
                    <template v-if="column.key === 'command'">
                        {{ commandSummary(record) }}
                    </template>
                    <template v-if="column.key === 'action'">
                        <a-space>
                            <a-button type="link" size="small" @click="openEditModal(record)">编辑</a-button>
                            <a-popconfirm title="确定删除？" @confirm="handleDelete(record.id)">
                                <a-button type="link" danger size="small">删除</a-button>
                            </a-popconfirm>
                        </a-space>
                    </template>
                </template>
            </a-table>

            <!-- 执行日志 -->
            <div style="margin-top:32px;">
                <div class="control-section-title">
                    执行日志
                    <a-button type="link" size="small" @click="loadLogs">刷新</a-button>
                    <a-popconfirm title="清理7天前的日志？" @confirm="clearLogs">
                        <a-button type="link" danger size="small">清理</a-button>
                    </a-popconfirm>
                </div>
                <a-table :dataSource="logs" :columns="logColumns" :pagination="logPagination" @change="handleLogTableChange" size="small" rowKey="id">
                    <template #bodyCell="{ column, record }">
                        <template v-if="column.key === 'success'">
                            <a-tag :color="record.success ? 'green' : 'red'">{{ record.success ? '成功' : '失败' }}</a-tag>
                        </template>
                        <template v-if="column.key === 'time'">
                            {{ formatDate(record.createdAt) }}
                        </template>
                        <template v-if="column.key === 'command'">
                            {{ translateCommand(record.command) }}
                        </template>
                        <template v-if="column.key === 'deviceId'">
                            {{ getDeviceName(record.deviceId) }}
                        </template>
                    </template>
                </a-table>
            </div>

            <!-- 添加/编辑弹窗 -->
            <a-modal v-model:open="modalVisible" :title="editingTask ? '编辑任务' : '添加任务'" @ok="handleSubmit" okText="保存" width="640px">
                <a-form layout="vertical">
                    <a-form-item label="任务名称" required>
                        <a-input v-model:value="form.taskName" placeholder="如：每晚定时播放" />
                    </a-form-item>
                    <a-form-item label="选择设备" required>
                        <a-select v-model:value="form.deviceId" placeholder="选择设备" style="width:100%" :options="deviceOptions" />
                    </a-form-item>
                    <a-form-item label="执行命令" required>
                        <div v-for="(cmd, idx) in form.commands" :key="idx" style="margin-bottom:12px; padding:12px; background:#fafafa; border-radius:8px;">
                            <div style="display:flex; gap:8px; align-items:center; margin-bottom:8px;">
                                <span style="color:#999; width:24px;">{{ idx + 1 }}.</span>
                                <a-select v-model:value="cmd.cmd" placeholder="选择命令" style="flex:1;">
                                    <a-select-option v-for="(label, key) in commandMap" :key="key" :value="key">{{ label }}</a-select-option>
                                </a-select>
                                <a-button v-if="form.commands.length > 1" type="text" danger @click="removeCommand(idx)" size="small">✕</a-button>
                            </div>
                            <div v-if="cmd.cmd === 'set_volume'" style="padding-left:32px;">
                                <a-select v-model:value="cmd.params.volume" style="width:120px;">
                                    <a-select-option v-for="v in volumeOptions" :key="v" :value="v">{{ v }}</a-select-option>
                                </a-select>
                            </div>
                            <div v-if="cmd.cmd === 'set_play_mode'" style="padding-left:32px;">
                                <a-select v-model:value="cmd.params.mode" style="width:200px;">
                                    <a-select-option v-for="(label, key) in playModes" :key="key" :value="Number(key)">{{ label }}</a-select-option>
                                </a-select>
                            </div>
                            <div v-if="cmd.cmd === 'tts'" style="padding-left:32px;">
                                <a-input v-model:value="cmd.params.text" placeholder="播报文本" />
                            </div>
                            <div v-if="cmd.cmd === 'send_command'" style="padding-left:32px; display:flex; gap:8px;">
                                <a-input v-model:value="cmd.params.text" placeholder="命令文本" style="flex:1;" />
                                <a-input v-model:value="cmd.params.did" placeholder="设备DID" style="width:180px;" />
                            </div>
                        </div>
                        <a-button type="dashed" @click="addCommand" style="width:100%;">+ 添加命令</a-button>
                    </a-form-item>
                    <a-form-item label="Cron 表达式（秒 分 时 日 月 周）" required>
                        <a-input v-model:value="form.cronExpr" placeholder="如：0 0 8 * * ?">
                            <template #addonAfter>
                                <a-popover trigger="click" placement="topRight">
                                    <template #content>
                                        <div style="width:320px;">
                                            <div v-for="p in cronPresets" :key="p.expr" style="padding:4px 0;">
                                                <a-button type="link" size="small" @click="form.cronExpr = p.expr">{{ p.label }}</a-button>
                                                <code style="margin-left:8px; color:#999;">{{ p.expr }}</code>
                                            </div>
                                        </div>
                                    </template>
                                    <template #title>常用表达式</template>
                                    <a-button type="link" size="small">常用</a-button>
                                </a-popover>
                            </template>
                        </a-input>
                        <div v-if="cronDesc" style="margin-top:4px; color:#999; font-size:12px;">{{ cronDesc }}</div>
                    </a-form-item>
                    <a-form-item label="启用">
                        <a-switch v-model:checked="form.enabled" />
                    </a-form-item>
                </a-form>
            </a-modal>
        </main>
    </div>
    `,
    data() {
        return {
            tasks: [],
            logs: [],
            devices: [],
            commandMap: {},
            playModes: {},
            modalVisible: false,
            editingTask: null,
            form: { taskName: '', deviceId: undefined, cronExpr: '', commands: [{ cmd: undefined, params: {} }], enabled: true },
            volumeOptions: [5, 10, 15, 20, 25, 30, 35, 40, 45, 50],
            cronPresets: [
                { label: '每5分钟', expr: '0 0/5 * * * ?' },
                { label: '每30分钟', expr: '0 0/30 * * * ?' },
                { label: '每小时', expr: '0 0 * * * ?' },
                { label: '每小时15分', expr: '0 15 * * * ?' },
                { label: '每天 08:00', expr: '0 0 8 * * ?' },
                { label: '每天 12:00', expr: '0 0 12 * * ?' },
                { label: '每天 18:00', expr: '0 0 18 * * ?' },
                { label: '每天 22:00', expr: '0 0 22 * * ?' },
                { label: '工作日 09:00', expr: '0 0 9 * * MON-FRI' },
                { label: '工作日 18:00', expr: '0 0 18 * * MON-FRI' },
                { label: '每周一 08:00', expr: '0 0 8 * * MON' },
                { label: '每月1号 08:00', expr: '0 0 8 1 * ?' },
            ],
            taskColumns: [
                { title: '任务名称', dataIndex: 'taskName', key: 'taskName' },
                { title: 'Cron', key: 'cronExpr' },
                { title: '命令', key: 'command' },
                { title: '启用', key: 'enabled', width: 80 },
                { title: '操作', key: 'action', width: 140 },
            ],
            logColumns: [
                { title: '时间', key: 'time', width: 170 },
                { title: '任务', dataIndex: 'taskName', key: 'taskName' },
                { title: '设备', key: 'deviceId', width: 120, ellipsis: true },
                { title: '命令', key: 'command' },
                { title: '结果', key: 'success', width: 80 },
                { title: '消息', dataIndex: 'message', key: 'message', ellipsis: true },
            ],
            logPagination: {
                current: 1,
                pageSize: 10,
                total: -1,
                showSizeChanger: true,
                pageSizeOptions: ['10', '20', '30'],
                showTotal: (total) => `共 ${total} 条`,
            },
        };
    },
    computed: {
        deviceOptions() {
            return this.devices.map(d => ({ value: d.deviceId, label: d.name + ' (' + d.model + ')' }));
        },
        cronDesc() {
            const expr = this.form.cronExpr?.trim();
            if (!expr) return '';
            const parts = expr.split(/\s+/);
            if (parts.length !== 6) return '';
            const [sec, min, hour, day, month, week] = parts;
            let desc = '';
            if (week !== '?') desc += week + ' ';
            if (day !== '*') desc += '每月' + day + '号 ';
            if (hour !== '*') desc += hour + '时';
            if (min !== '*' && !min.includes('/')) desc += min + '分';
            if (min.includes('/')) desc += '每' + min.split('/')[1] + '分钟';
            if (hour === '*' && min === '*' && sec === '0') desc = '每分钟';
            return desc ? '→ ' + desc : '';
        },
    },
    async mounted() {
        await this.loadDevices();
        this.loadCommands();
        this.loadPlayModes();
        this.loadTasks();
        this.loadLogs();
    },
    methods: {
        async loadDevices() {
            try {
                const res = await api.devices.list();
                if (res.success) this.devices = res.data || [];
            } catch (e) {}
        },
        async loadCommands() {
            try {
                const res = await api.schedule.commands();
                if (res.success) this.commandMap = res.data || {};
            } catch (e) {}
        },
        async loadPlayModes() {
            try {
                const res = await api.schedule.playModes();
                if (res.success) this.playModes = res.data || {};
            } catch (e) {}
        },
        async loadTasks() {
            try {
                const res = await api.schedule.tasks();
                if (res.success) this.tasks = res.data || [];
            } catch (e) {}
        },
        async loadLogs() {
            try {
                const res = await api.schedule.logs(null, this.logPagination.current, this.logPagination.pageSize, this.logPagination.total);
                if (res.success && res.data) {
                    this.logs = res.data.list || [];
                    this.logPagination.total = res.data.total || 0;
                }
            } catch (e) {}
        },
        async clearLogs() {
            await api.schedule.clearLogs();
            this.logPagination.current = 1;
            this.logPagination.total = -1;
            this.loadLogs();
        },
        handleLogTableChange(pagination) {
            this.logPagination.current = pagination.current;
            this.logPagination.pageSize = pagination.pageSize;
            this.loadLogs();
        },
        formatDate(createdAt) {
            if (!createdAt) return '-';
            if (typeof createdAt === 'number') return new Date(createdAt * 1000).toLocaleString();
            return new Date(createdAt).toLocaleString();
        },
        translateCommand(command) {
            if (!command) return '-';
            return command.split(' → ').map(c => this.commandMap[c] || c).join(' → ');
        },
        getDeviceName(deviceId) {
            if (!deviceId) return '-';
            const device = this.devices.find(d => d.deviceId === deviceId);
            return device ? device.name : deviceId;
        },
        commandSummary(record) {
            try {
                const cmds = JSON.parse(record.command);
                if (Array.isArray(cmds)) {
                    return cmds.map(c => this.commandMap[c.cmd] || c.cmd).join(' → ');
                }
            } catch (e) {}
            return this.commandMap[record.command] || record.command;
        },
        addCommand() {
            this.form.commands.push({ cmd: undefined, params: {} });
        },
        removeCommand(idx) {
            this.form.commands.splice(idx, 1);
        },
        openAddModal() {
            this.editingTask = null;
            this.form = { taskName: '', deviceId: this.devices[0]?.deviceId, cronExpr: '', commands: [{ cmd: undefined, params: {} }], enabled: true };
            this.modalVisible = true;
        },
        openEditModal(record) {
            this.editingTask = record;
            let commands = [];
            try {
                const parsed = JSON.parse(record.command);
                if (Array.isArray(parsed)) {
                    commands = parsed;
                } else {
                    commands = [{ cmd: record.command }];
                }
            } catch (e) {
                commands = [{ cmd: record.command }];
            }
            commands = commands.map(c => ({
                cmd: c.cmd,
                params: {
                    volume: c.params?.volume || 20,
                    mode: c.params?.mode != null ? c.params.mode : 2,
                    text: c.params?.text || '',
                    did: c.params?.did || '',
                },
            }));
            this.form = {
                taskName: record.taskName,
                deviceId: record.deviceId,
                cronExpr: record.cronExpr,
                commands: commands,
                enabled: record.enabled,
            };
            this.modalVisible = true;
        },
        async handleSubmit() {
            if (!this.form.taskName || !this.form.cronExpr || !this.form.deviceId) {
                this.$message.warning('请填写完整信息');
                return;
            }
            const cronRes = await api.schedule.validateCron(this.form.cronExpr);
            if (!cronRes.success) {
                this.$message.error(cronRes.message || 'Cron表达式无效');
                return;
            }
            const validCmds = this.form.commands.filter(c => c.cmd);
            if (validCmds.length === 0) {
                this.$message.warning('请至少添加一个命令');
                return;
            }
            const commands = validCmds.map(c => {
                const entry = { cmd: c.cmd };
                if (c.cmd === 'tts') entry.params = { text: c.params.text || '定时播报' };
                else if (c.cmd === 'send_command') entry.params = { text: c.params.text, did: c.params.did };
                else if (c.cmd === 'set_volume') entry.params = { volume: c.params.volume };
                else if (c.cmd === 'set_play_mode') entry.params = { mode: c.params.mode };
                return entry;
            });
            const data = {
                taskName: this.form.taskName,
                deviceId: this.form.deviceId,
                cronExpr: this.form.cronExpr,
                commands: commands,
                enabled: this.form.enabled,
            };
            try {
                if (this.editingTask) {
                    var res = await api.schedule.updateTask(this.editingTask.id, data);
                } else {
                    var res = await api.schedule.addTask(data);
                }
                if (!res.success) {
                    this.$message.error(res.message || '保存失败');
                    return;
                }
                this.modalVisible = false;
                this.loadTasks();
            } catch (e) {
                this.$message.error('保存失败: ' + (e.message || '未知错误'));
            }
        },
        async handleToggle(record, val) {
            await api.schedule.updateTask(record.id, { enabled: val });
            this.loadTasks();
        },
        async handleDelete(id) {
            await api.schedule.deleteTask(id);
            this.loadTasks();
        },
    },
};

// === 语音命令页组件 ===
const VoiceCommandPage = {
    template: `
    <div class="app-layout">
        <header class="app-header">
            <div class="app-header-title">
                <span class="logo-dot"></span>
                小米音箱控制台
            </div>
            <a-button size="small" @click="$router.push('/devices')">返回设备列表</a-button>
        </header>
        <main class="app-content">
            <div class="control-header">
                <h2>语音命令</h2>
            </div>
            <!-- 轮询控制 -->
            <div class="control-section">
                <div class="control-section-title">对话监听</div>
                <a-row :gutter="16" align="middle">
                    <a-col :span="8">
                        <a-select v-model:value="pollDeviceId" placeholder="选择设备" style="width:100%" :options="deviceOptions" />
                    </a-col>
                    <a-col :span="4">
                        <a-select v-model:value="pollInterval" style="width:100%">
                            <a-select-option :value="1">1秒</a-select-option>
                            <a-select-option :value="2">2秒</a-select-option>
                            <a-select-option :value="3">3秒</a-select-option>
                            <a-select-option :value="5">5秒</a-select-option>
                        </a-select>
                    </a-col>
                    <a-col :span="6">
                        <a-switch v-model:checked="polling" @change="togglePolling" />
                        <span style="margin-left:8px">{{ polling ? '监听中' : '已停止' }}</span>
                    </a-col>
                </a-row>

                <!-- 实时对话流 -->
                <div v-if="polling" style="margin-top:16px; max-height:300px; overflow-y:auto; background:#fafafa; border-radius:8px; padding:12px;">
                    <div v-for="evt in voiceEvents" :key="evt.timestamp" style="padding:6px 0; border-bottom:1px solid #f0f0f0;">
                        <a-tag :color="evt.handled ? 'green' : 'orange'" style="margin-right:8px;">{{ evt.matchedCommand || '未匹配' }}</a-tag>
                        <span style="color:#666; font-size:12px;">{{ formatTime(evt.timestamp) }}</span>
                        <span style="margin-left:8px;">{{ evt.query }}</span>
                    </div>
                    <div v-if="voiceEvents.length === 0" style="color:#999; text-align:center; padding:20px;">
                        等待语音输入...
                    </div>
                </div>
            </div>

            <!-- 发送文本命令 -->
            <div class="control-section">
                <div class="control-section-title">发送文本命令</div>
                <a-row :gutter="16" align="middle">
                    <a-col :span="14">
                        <a-input v-model:value="sendText" placeholder="输入文本命令（等同于对音箱说话）" @pressEnter="handleSend" />
                    </a-col>
                    <a-col :span="4">
                        <a-checkbox v-model:checked="sendSpeak">语音回应</a-checkbox>
                    </a-col>
                    <a-col :span="4">
                        <a-button type="primary" @click="handleSend" :loading="sendLoading">发送</a-button>
                    </a-col>
                </a-row>
            </div>

            <!-- 关键词配置 -->
            <div class="control-section">
                <div class="control-section-title">
                    关键词配置
                    <a-button type="link" size="small" @click="showAddKeyword = true">+ 添加</a-button>
                </div>
                <a-table :dataSource="allKeywords" :columns="keywordColumns" :pagination="false" size="small" rowKey="id">
                    <template #bodyCell="{ column, record }">
                        <template v-if="column.key === 'enabled'">
                            <a-switch v-if="!record.builtin" :checked="record.enabled" @change="(v) => handleKeywordToggle(record, v)" size="small" />
                            <span v-else style="color:#999;">内置</span>
                        </template>
                        <template v-if="column.key === 'action'">
                            <template v-if="!record.builtin">
                                <a-popconfirm title="确定删除？" @confirm="handleDeleteKeyword(record.id)">
                                    <a-button type="link" danger size="small">删除</a-button>
                                </a-popconfirm>
                            </template>
                        </template>
                    </template>
                </a-table>

                <a-modal v-model:open="showAddKeyword" title="添加关键词" @ok="handleAddKeyword" okText="添加">
                    <a-form layout="vertical">
                        <a-form-item label="关键词">
                            <a-input v-model:value="newKeyword" placeholder="如：播放歌曲" />
                        </a-form-item>
                        <a-form-item label="命令动作">
                            <a-input v-model:value="newCommand" placeholder="如：play_next 或 exec#自定义文本" />
                        </a-form-item>
                    </a-form>
                </a-modal>
            </div>
        </main>
    </div>
    `,
    data() {
        return {
            devices: [],
            pollDeviceId: undefined,
            pollInterval: 1,
            polling: false,
            voiceEvents: [],
            eventSource: null,
            sendText: '',
            sendSpeak: false,
            sendLoading: false,
            builtinKeywords: {},
            customKeywords: [],
            showAddKeyword: false,
            newKeyword: '',
            newCommand: '',
            keywordColumns: [
                { title: '关键词', dataIndex: 'keyword', key: 'keyword' },
                { title: '命令', dataIndex: 'command', key: 'command' },
                { title: '类型', key: 'type', customRender: ({ record }) => record.id ? '自定义' : '内置' },
                { title: '启用', key: 'enabled' },
                { title: '操作', key: 'action' },
            ],
        };
    },
    computed: {
        deviceOptions() {
            return this.devices.map(d => ({ value: d.deviceId, label: d.name + ' (' + d.model + ')' }));
        },
        allKeywords() {
            const builtin = Object.entries(this.builtinKeywords).map(([k, v]) => ({ keyword: k, command: v, enabled: true, builtin: true }));
            return [...this.customKeywords, ...builtin];
        },
    },
    async mounted() {
        await this.loadDevices();
        this.loadKeywords();
        this.loadPollingStatus();
    },
    beforeUnmount() {
        this.closeEventSource();
    },
    methods: {
        async loadDevices() {
            try {
                const res = await api.devices.list();
                if (res.success) this.devices = res.data || [];
            } catch (e) {}
        },
        async loadPollingStatus() {
            try {
                const res = await api.voice.pollingStatus();
                if (res.success && res.data.enabled) {
                    this.polling = true;
                    this.pollDeviceId = res.data.deviceId;
                    this.pollInterval = res.data.interval || 1;
                    this.connectEventSource();
                }
            } catch (e) {}
        },
        async togglePolling(val) {
            if (val) {
                if (!this.pollDeviceId) { this.polling = false; return; }
                await api.voice.startPolling(this.pollDeviceId, this.pollInterval);
                this.voiceEvents = [];
                this.connectEventSource();
            } else {
                await api.voice.stopPolling();
                this.closeEventSource();
            }
        },
        connectEventSource() {
            this.closeEventSource();
            this.eventSource = new EventSource(api.voice.streamUrl());
            this.eventSource.addEventListener('conversation', (e) => {
                const data = JSON.parse(e.data);
                this.voiceEvents.unshift(data);
                if (this.voiceEvents.length > 100) this.voiceEvents.pop();
            });
        },
        closeEventSource() {
            if (this.eventSource) { this.eventSource.close(); this.eventSource = null; }
        },
        async handleSend() {
            if (!this.sendText.trim()) return;
            const device = this.devices.find(d => d.deviceId === this.pollDeviceId || d.deviceId === this.devices[0]?.deviceId);
            if (!device) return;
            this.sendLoading = true;
            try {
                await api.voice.send(device.did, this.sendText, this.sendSpeak);
                this.sendText = '';
            } finally {
                this.sendLoading = false;
            }
        },
        async loadKeywords() {
            try {
                const res = await api.voice.keywords();
                if (res.success) {
                    this.builtinKeywords = res.data.builtin || {};
                    this.customKeywords = res.data.custom || [];
                }
            } catch (e) {}
        },
        async handleAddKeyword() {
            if (!this.newKeyword || !this.newCommand) return;
            await api.voice.addKeyword(this.newKeyword, this.newCommand);
            this.showAddKeyword = false;
            this.newKeyword = '';
            this.newCommand = '';
            this.loadKeywords();
        },
        async handleKeywordToggle(record, val) {
            if (record.id) {
                await api.voice.updateKeyword(record.id, record.keyword, record.command, val);
                this.loadKeywords();
            }
        },
        async handleDeleteKeyword(id) {
            await api.voice.deleteKeyword(id);
            this.loadKeywords();
        },
        formatTime(ts) {
            return new Date(ts).toLocaleTimeString();
        },
    },
};

// === 路由 ===
const routes = [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginPage },
    { path: '/devices', component: DevicesPage },
    { path: '/control', component: ControlPage },
    { path: '/voice', component: VoiceCommandPage },
    { path: '/schedule', component: SchedulePage },
];

const router = VueRouter.createRouter({
    history: VueRouter.createWebHistory(),
    routes,
});

// === 应用 ===
const app = Vue.createApp({});
app.use(antd);
app.use(router);
app.mount('#app');
