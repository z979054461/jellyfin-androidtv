# Jellyfin TV 个人定制版

基于官方 [jellyfin/jellyfin-androidtv v0.19.10](https://github.com/jellyfin/jellyfin-androidtv/releases/tag/v0.19.10)，当前定制版本 **0.19.10-danmaku.5**。这是个人 fork，**不是 Jellyfin 官方发布**。下方保留的上游商店链接只对应官方客户端，不是本定制 APK。

- [下载本 fork 的版本](https://github.com/z979054461/jellyfin-androidtv/releases)
- [第 5 版说明](docs/releases/danmaku-release-5.md)
- [弹幕协议、边界与测试清单](docs/native-danmaku.md)
- 官方基线提交：`984181a3d6ab14e9a6d2dcc850c582e1c138bd95`。新增上游功能需要先合并、检查冲突并重新测试，不会因为官方发布就自动进入已安装的 APK。

## 相对官方 v0.19.10 的定制差异

1. **原生弹幕。** 内置播放器的可定位点播支持滚动、顶部和底部弹幕。默认开启，明确关闭后会保留选择；控制栏可切换、查看加载状态和重新加载。支持字体、行数、透明度、持续时间及时间偏移。新安装字号默认 80%，可选 50%–150%（每档 10%）；行数可选 1–20 的每个整数，实际行数受字号和字幕安全区域限制，底部保留 30%。已有设置不会被覆盖。直播、外部播放器和不可定位流不显示弹幕。
2. **弹幕数据与安全边界。** 从已连接 Jellyfin 服务器的 `/api/danmu/{itemId}/raw` 获取 Bilibili 风格 XML，兼容已有 jellyfin-plugin-danmu；不自动安装服务器插件、不向第三方弹幕网站请求，也不自动匹配剧集。使用现有服务器授权，拒绝重定向；限制响应大小、时长、条数、文本长度和 XML 嵌套，禁止 DTD/外部实体。缺少插件或请求失败不阻止视频播放。切集、关闭弹幕及销毁页面会取消旧请求。
3. **播放信息。** 控制栏增加只读播放信息面板：播放方式、容器、源音视频格式和播放器当前输入格式等。面板打开时更新，切集时关闭，不为此额外请求服务器，也不显示媒体地址或令牌。输入格式不是电视最终解码输出，面板不代表网络测速。
4. **加载网速（第 4 版）。** 播放操作栏出现时，在顶部头像左侧默认显示，不需要设置开关，操作栏隐藏即停止 UI 刷新。统计当前播放器收到的实际网络载荷字节，约 2 秒滑动窗口、每秒刷新；单位为十进制 B/s、kB/s、MB/s，并非视频码率或线路带宽。包含当前媒体及其清单/外部字幕下载，不包含封面、弹幕、API 等其他请求；不额外下载文件测速。不计本地读取，不汇总设备其他 App 流量。区分等待数据、缓冲播放、暂停、空闲和本地播放。暂停后播放器仍可能短暂预读，所以可显示真实非零速率；已有缓冲时 0 B/s 不代表网络故障。切集和退出会废弃旧计数器；不记录媒体 URL。
5. **按住临时 2×（第 4 版）。** 已在播放、可定位的点播中，长按遥控器播放或播放/暂停键临时切到 2×，松开恢复此前实际速度，不修改保存的速度选择。操作栏隐藏、没有菜单/节目表/跳过提示时，确认/Enter 键也支持；其他焦点操作不被抢占。短按仍保留原有操作，出现 `2×` 提示时才表示已触发。失焦、暂停、停止、切集、离开页面或主动改速度时恢复。为防遥控器遗漏松开事件，单次最长 30 秒，随后恢复；要继续需松开再按。直播和不可定位流不启用。不同电视的系统可能拦截硬件媒体键，需实机验证。
6. **应用内更新。** 设置 → 关于提供手动检查更新、确认下载；第 5 版在本次下载并校验成功后直接打开 Android 系统安装器，由用户最终确认。从固定 fork 的 GitHub Releases 选择更高版本，允许本定制版标记为 prerelease 的正式签名包；校验受限 HTTPS 下载来源、大小、GitHub SHA-256、包名、递增版本号和签名。不后台自动更新、不静默安装、不绕过“允许安装未知应用”授权；电视必须能访问 GitHub。更新检查使用 GitHub，不会把 Jellyfin 服务器凭据发过去。
7. **应用标识、签名与版本。** 正式包名为 `io.github.z979054461.jellyfin.danmaku`，第 5 版 versionCode 为 `191005`，APK 为 release 构建、`debuggable=false`，沿用专用固定签名。与官方客户端及旧 `.debug` 预览版并存，首次迁移到正式版需重新登录；第 3 版及以后可以覆盖升级并保留应用数据，不用卸载。公开证书指纹在 `config/danmaku-signing.sha256`，私钥不在源码或 APK 附件中。
8. **图标与电视横幅。** 使用新的 Jellyfin 风格图标及 TV banner，不含弹幕图案；这是本 fork 的视觉定制，不代表官方品牌背书。
9. **发布与测试流程。** 特性分支增加签名 release CI、独立弹幕/更新/网络计量/长按状态测试和发布身份校验。签名材料仅在 CI 临时使用并清理；缺少 Secrets 时仍检查但不发布，不覆盖既有 tag/资产。保留上游 lint/detekt 策略，任务成功不意味着所有历史警告已消除。第 4 版还去掉播放器直接记录完整媒体/字幕 URL 的日志，减少地址中凭据暴露。


10. **更新页遥控操作与安装器兼容（第 5 版）。** 给更新动作设置稳定标识，异步检查/下载重建页面后恢复到可操作项：任务执行中到取消、下载完成后自动打开系统安装器、返回或失败时可聚焦重试。返回系统界面及重新进入页面也恢复有效焦点；不在页面离开后抢焦点。下载及校验成功后，在页面仍处于前台时仅拉起一次系统安装器；用户离开、取消或返回时不自动拉起。已校验 APK 同时保留“打开系统安装器”重试动作，安装权限设置单独列出，不把 `canRequestPackageInstalls()` 的结果当作唯一入口。只在系统没有对应 Intent 处理器时尝试另一种标准安装 Intent，安全拒绝不会触发替代尝试。系统仍可拒绝并要求用户确认；不改全局安全设置、不调用厂商隐藏组件、不静默安装。安装器入口失败不会无故清掉已校验 APK；再次安装前仍重新验证文件。

没有改动 Jellyfin 服务器、媒体库、账号权限或服务器插件配置；未复制第三方客户端的整套功能。保留上游 GPL v2 LICENSE 及声明。弹幕行为和协议参考 [fengymi/jellyfin-androidtv v1300.19.6](https://github.com/fengymi/jellyfin-androidtv)，渲染器、解析和时间轴独立实现，未引入其 DanmakuFlameMaster 源码/二进制。未声称对所有已有上游依赖做了新的全面许可证审计。

## 构建与验证

使用 JDK 21、Android SDK 36 及仓库 Gradle wrapper：

```sh
./gradlew assembleRelease test detekt lint
bash tools/danmaku-tests/run.sh
bash tools/update-tests/run.sh
sh tools/network-tests/run.sh
sh tools/network-label-tests/run.sh
sh tools/hold-speed-tests/run.sh
sh tools/installer-tests/run.sh
```

普通本地 release 构建没有发布私钥；不要把自己的签名密钥提交到仓库。正式发布由 CI 使用专用加密 Secrets。单元测试不等于真实电视兼容性保证，遥控器、系统安装器、直播/转码/字幕与服务器插件仍需实机回归。

---

## 上游项目说明（保留）

以下介绍、商店链接与贡献入口来自官方项目，适用于官方客户端。

<h1 align="center">Jellyfin Android TV</h1>
<h3 align="center">Part of the <a href="https://jellyfin.org">Jellyfin Project</a></h3>

---

<p align="center">
<img alt="Logo banner" src="https://raw.githubusercontent.com/jellyfin/jellyfin-ux/master/branding/SVG/banner-logo-solid.svg?sanitize=true"/>
<br/><br/>
<a href="https://github.com/jellyfin/jellyfin-androidtv">
<img alt="GPL 2.0 License" src="https://img.shields.io/github/license/jellyfin/jellyfin-androidtv.svg"/>
</a>
<a href="https://github.com/jellyfin/jellyfin-androidtv/releases">
<img alt="Current Release" src="https://img.shields.io/github/release/jellyfin/jellyfin-androidtv.svg"/>
</a>
<a href="https://translate.jellyfin.org/projects/jellyfin-android/jellyfin-androidtv/">
<img alt="Translation Status" src="https://translate.jellyfin.org/widgets/jellyfin-android/-/jellyfin-androidtv/svg-badge.svg"/>
</a>
<br/>
<a href="https://opencollective.com/jellyfin">
<img alt="Donate" src="https://img.shields.io/opencollective/all/jellyfin.svg?label=backers"/>
</a>
<a href="https://features.jellyfin.org">
<img alt="Feature Requests" src="https://img.shields.io/badge/fider-vote%20on%20features-success.svg"/>
</a>
<a href="https://matrix.to/#/+jellyfin:matrix.org">
<img alt="Chat on Matrix" src="https://img.shields.io/matrix/jellyfin:matrix.org.svg?logo=matrix"/>
</a>
<a href="https://www.reddit.com/r/jellyfin">
<img alt="Join our Subreddit" src="https://img.shields.io/badge/reddit-r%2Fjellyfin-%23FF5700.svg"/>
</a>
<br/>
<a href="https://play.google.com/store/apps/details?id=org.jellyfin.androidtv">
<img width="153" alt="Jellyfin on Google Play" src="https://jellyfin.org/images/store-icons/google-play.png"/>
</a>
<a href="https://www.amazon.com/gp/aw/d/B07TX7Z725">
<img width="153" alt="Jellyfin on Amazon Appstore" src="https://jellyfin.org/images/store-icons/amazon.png"/>
</a>
<a href="https://f-droid.org/en/packages/org.jellyfin.androidtv/">
<img width="153" alt="Jellyfin on F-Droid" src="https://jellyfin.org/images/store-icons/fdroid.png"/>
</a>
<br/>
<a href="https://repo.jellyfin.org/releases/client/androidtv/">Download archive</a>
</p>

Jellyfin Android TV is a Jellyfin client for Android TV, Nvidia Shield, and Amazon Fire TV devices.
We welcome all contributions and pull requests! If you have a larger feature in mind please open an
issue so we can discuss the implementation before you start. 

## Translating

Translations can be improved very easily from our
[Weblate](https://translate.jellyfin.org/projects/jellyfin-android/jellyfin-androidtv) instance.
Look through the following graphic to see if your native language could use some work!

<a href="https://translate.jellyfin.org/engage/jellyfin-android/">
<img alt="Detailed Translation Status" src="https://translate.jellyfin.org/widgets/jellyfin-android/-/jellyfin-androidtv/multi-auto.svg"/>
</a>

## Build Process

### Dependencies

- Android Studio

### Build

1. Clone or download this repository

   ```sh
   git clone https://github.com/jellyfin/jellyfin-androidtv.git
   cd jellyfin-androidtv
   ```

2. Open the project in Android Studio and run it from there or build an APK directly through Gradle:

   ```sh
   ./gradlew assembleDebug
   ```
   
   Add the Android SDK to your PATH environment variable or create the ANDROID_SDK_ROOT variable for
   this to work.

### Deploy to device/emulator

   ```sh
   ./gradlew installDebug
   ```

*You can also replace the "Debug" with "Release" to get an optimized release binary.*
