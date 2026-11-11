# Jellyfin TV 个人定制版

基于 [Jellyfin Android TV v0.20.0](https://github.com/jellyfin/jellyfin-androidtv/releases/tag/v0.20.0)，面向 Android TV、Nvidia Shield 和 Fire TV。个人维护，非 Jellyfin 官方发行版。

**此分支为 0.20.0 集成预览，尚未发布。** 最低 Android 6（API 23）。

最近已发布版本：0.19.10-danmaku.5 · [下载 APK](https://github.com/z979054461/jellyfin-androidtv/releases/tag/v0.19.10-danmaku.5) · [更新记录](docs/releases/danmaku-release-5.md)

## 相对官方版的变化

- **原生弹幕**：支持滚动、顶部和底部弹幕，默认开启；可调整字号、行数、透明度、速度和时间偏移，并查看加载状态、重新加载。随暂停、拖动、续播及倍速同步。
- **播放信息**：在控制栏查看直接播放/转码状态、容器和音视频格式。
- **实时加载网速**：呼出播放控制栏后，顶部显示播放器实际接收的数据速率。
- **长按临时 2×**：长按播放键临时加速，松开恢复原速度；控制栏隐藏时也支持确认键。
- **应用内更新**：设置 → 关于检查更新，下载校验后打开系统安装器。修复下载完成、取消及返回后的遥控器焦点，支持安装重试。
- **独立安装与外观**：独立包名、固定签名、新图标和电视横幅，可与官方版并存。
- **日志与发布检查**：不再直接记录完整媒体/字幕 URL；增加定制功能回归测试和 APK 签名、包名、版本校验。

[完整功能与开发说明](docs/custom-changes.md)

## 使用与升级

1. 从本仓库 Releases 下载 APK，在电视上安装。
2. 登录自己的 Jellyfin 服务器，使用内置播放器播放视频。
3. 如需弹幕，服务器须已安装兼容的 [jellyfin-plugin-danmu](https://github.com/cxfksword/jellyfin-plugin-danmu)，并准备好对应影片的弹幕文件。客户端不会自动安装插件或搜索第三方弹幕。

第 3 版及之后的正式包可直接覆盖升级，无需卸载。旧 `.debug` 预览版与正式版是不同应用，首次安装正式版需重新登录。应用内更新需要电视能访问 GitHub，安装由 Android 系统确认。

弹幕与临时倍速适用于可拖动的点播视频，不适用于直播或外部播放器。不同电视的按键与安装权限可能有差异。

## 开发与许可

构建环境与测试命令见[开发说明](docs/custom-changes.md#构建与验证)，弹幕协议见[技术文档](docs/native-danmaku.md)。

上游项目：[jellyfin/jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv)。保留 [GPL v2 许可证](LICENSE)及上游声明。弹幕协议与行为参考 [fengymi/jellyfin-androidtv](https://github.com/fengymi/jellyfin-androidtv)，渲染、解析与时间轴独立实现。
