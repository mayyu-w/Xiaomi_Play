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
                <a-button @click="loadDevices" :loading="loading">刷新</a-button>
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
                    <span>当前播放：</span><span class="playing-icon">♪</span> {{ currentFileName }}
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
            playStartTime: 0,
            pendingNewSong: false,
            sseSource: null,
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
            if (!this.selectedFile) return '';
            const parts = this.selectedFile.replace(/\\/g, '/').split('/');
            return parts[parts.length - 1];
        },
    },
    methods: {
        getDeviceId() {
            return this.deviceId || this.did;
        },
        async handlePause() {
            this.manualPause = true;
            this.musicLoading = true;
            try {
                const res = await api.music.pause(this.getDeviceId());
                res.success ? this.$message.success('已暂停') : this.$message.error(res.message);
            } catch { this.$message.error('网络错误'); }
            finally { this.musicLoading = false; }
        },
        async handleResume() {
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
            this.musicLoading = true;
            this.manualPause = false;
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

            if (now > this.volumeLockUntil) {
                this.volume = data.volume || 0;
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

            // 新歌等待后端确认（status=1 且 duration>0）
            if (this.pendingNewSong) {
                if (newStatus === 1 && newDuration > 0) {
                    this.pendingNewSong = false;
                    this.statusBasePlayTime = newPlayTime;
                    this.statusBaseTimestamp = now;
                    this.displayPlayTime = newPlayTime;
                    this.displayDuration = newDuration;
                    this.syncProgressPercent();
                    this.prevPlayStatus = newStatus;
                }
                this.resetCountdown();
                return;
            }
            // 回跳检测：设备已自动循环（position 从接近结尾跳回接近0）
            if (!this.manualPause && !this.autoPlaying
                && this.selectedFile && newDuration > 0
                && this.statusBasePlayTime > newDuration * 0.8
                && newPlayTime < this.statusBasePlayTime - 5) {
                this.progressActive = false;
                this.handleSongEnd();
                this.resetCountdown();
                return;
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
        this.stopSSE();
        this.stopCountdown();
        this.stopProgressTimer();
    },
};

// === 路由 ===
const routes = [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginPage },
    { path: '/devices', component: DevicesPage },
    { path: '/control', component: ControlPage },
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
