# XBot Android

XBot 的 Android 信号查看器和桌面小组件。应用登录现有 XBot 后端，展示当前账户的信号设置，并允许将单条信号固定到桌面。

## 已实现功能

- 自定义后端地址并登录
- 使用 Android Keystore（AES-GCM）加密保存访问令牌
- 展示信号品种、名称、周期/级别、方向和过期状态
- 独立警报管理页，展示 TradingView 警报配置、品种、周期及正常/停用状态
- 可选择警报管理页中显示的配置；首次默认显示前 6 项，选择结果保存在本机
- 警报配置默认按 `sort` 升序排列，未设置排序值的配置显示在最后
- 每项警报配置可为任意单个 TradingView 品种创建警报；支持完整代码或默认 Binance 简写
- 可删除单条已创建的 TradingView 警报，并在执行前二次确认
- 在应用内设置时间级别和过期时间，并同步刷新桌面小组件
- 支持手动刷新、退出登录及登录失效处理
- 为每个桌面小组件独立选择信号，并可选择是否显示品种名称
- 可从小组件右上角直接编辑已有小组件
- 支持维护信号名称到大图标族的映射，内置 MA-TREND 和 ATR-INDEX 的做多、做空、双向及关闭形态
- 可直接点击小组件的级别或过期时间，获取最新信号后快捷修改
- 小组件手动刷新，并通过 WorkManager 每 15 分钟后台刷新
- 后端暂时不可用时保留小组件的上次成功数据

## 本地运行

要求：JDK 17、Android SDK 36，设备最低 Android 8.0（API 26）。

后端监听宿主机 `3002` 端口时：

- Android Studio 模拟器使用 `http://10.0.2.2:3002/`
- 真机使用 `http://<宿主机局域网 IP>:3002/`
- 生产环境应使用 HTTPS；当前应用允许 HTTP 是为了支持局域网开发环境

构建并运行测试：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## 后端接口

应用当前依赖以下接口：

- `POST /api/customer/login`
- `GET /api/customer/signal-view/list`
- `GET /api/customer/signal-view/{id}`
- `PATCH /api/customer/signal-view/{id}/settings`
- `GET /api/customer/tv-alert/list`
- `POST /api/customer/tv-alert/all-alert-list`
- `POST /api/customer/tv-alert/add-alerts`
- `POST /api/customer/logout`

后端的 Customer Swagger 文档默认位于 `http://localhost:3002/customer-api`，OpenAPI JSON 位于 `http://localhost:3002/customer-api-json`。

## 桌面小组件

登录应用后，从系统的小组件选择器添加“信号设置”，选择一条信号并确认。点击小组件主体打开应用，点击右上角刷新图标立即更新。
