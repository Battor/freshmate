# FreshMate

食材保鲜管家——记录保质期，一眼看清什么快过期。

FreshMate is a simple, offline-first food freshness manager. Track what is in your fridge, see at a glance what expires soon, and stop throwing away forgotten food.

## 功能 / Features

- **过期分桶**：按剩余保鲜时间分组（已过期 / 即将过期 / 仍然新鲜），卡片带余量倒计时
- **快速录入**：名称、分类、生产日期、保质期，几秒记一笔；支持天/月/年与常用快捷时长
- **三段式编辑**：本次添加、继续编辑、浏览已有，分段吸附滚动
- **历史与恢复**：移除的条目保留在历史里，一键找回
- **过期提醒**：到期前定时通知
- **新手引导**：首次启动逐步演示主界面
- **主题与语言**：深浅色主题；简体中文 / 繁體中文 / English

## 隐私 / Privacy

完全离线可用，所有数据只保存在设备上——无需账号、无广告、无统计、无追踪。唯一的联网行为是可选的手动更新检查。

Fully offline. All data stays on your device — no account, no ads, no analytics, no tracking. The only network access is an optional manual update check.

## 下载 / Download

- F-Droid：上架流程进行中

## 构建 / Build

要求 JDK 17（Android Studio 自带 JBR 即可）。

```bash
./gradlew assembleDebug          # 调试包
./gradlew testDebugUnitTest      # 单元测试
```

正式签名配置从根目录 `keystore.properties` 读取（不入库）；更新检查地址从 `update.properties` 读取（不入库，缺失时回落到入库的公开默认地址，F-Droid 等源码构建环境无需该文件）。

## 许可证 / License

[GPL-3.0](LICENSE) © 2026 Battor

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
