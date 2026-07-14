# 卧槽邪教: Compose VPN

自由门安卓客户端逆向后, 用 Compose 重新包的一层最小软件.

## 行为

- 从 `libproxy.so` 启动本地原生代理。
- 等待代理就绪，并验证原始主页接口。
- 使用原始的 MTU、地址、路由、DNS 和会话参数创建 Android VPN 接口。
- 通过 `libtun2socks.so` 将 TUN 流量转发到本地代理。
- 按下“断开连接”时，停止 tun2socks 和原生代理。

不包含浏览器、WebView 代理注入、下载、播放器和更新服务。

## 构建

```powershell
./gradlew.bat :app:assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

应用 ID 为 `com.slimenull.wcxjvpn`，因此此版本可以与原应用同时安装。JNI 桥接类仍保留在 `com.dit.fgv.service` 包下，因为这些类名属于原生库 ABI 的一部分。