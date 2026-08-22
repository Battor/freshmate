# 需求-6 实现后三方审查记录（2026-08-22）

审查基线：`git diff main..HEAD`（feature/editing-neutral-colors，需求-6：编辑区配色中性化）。
审查方式：轻量模式——opus 合并审查 + `mobile-android-design` / `styles` 双 skill 只读并行重审，发现合并去重后一次性修复。

## 合并修复清单

| # | 来源 | 严重度 | 问题 | 修法 |
|---|---|---|---|---|
| 1 | opus#1 + MAD#1 | Important/P2 | `neutralFieldColors()` 只设 unfocused/focused 容器色，M3 1.3.0 `errorContainerColor` 默认透明——名称/保质期校验出错的瞬间填充消失透出卡片底，最需要稳定的时刻闪变 | 补 `errorContainerColor` + `disabledContainerColor`（opus#3 一并）= `surfaceContainerLowest` |
| 2 | opus#2 + MAD#4 | Minor/P3 | 品牌回落主题（Android 10–11）`surfaceContainer*` 回落 M3 基线偏紫灰，与 `LightBackground` 暖米色/`DarkBackground` 偏绿深底色相冲突，成为全页唯一外来色相 | `Color.kt` 新增 Light/Dark 派生色阶（从品牌底暖调派生），`Theme.kt` 显式定义 Low/Lowest/High/Highest + `onSurfaceVariant` |
| 3 | styles#1 + MAD#3 | P3 | EditingTargetCard KDoc「表单同底色」在 High→Highest 分档后不再成立 | KDoc 改为「中性底色比表单卡深一档（surfaceContainerHighest）」 |
| 4 | styles#2 | P3 | 四处 token 替换点无需求编号注释，helper KDoc 只覆盖输入框 | Card 处补 `// 需求-6：编辑区中性化…` 注释；helper KDoc 改 token 语义（兼修 MAD#2「近白」只描述浅色的问题，opus#4 同） |
| 5 | styles#3 | P3 | `neutralFieldColors()` 表达式函数体缺显式返回类型（邻居 `withAutoProductionDate` 有） | 补 `: TextFieldColors` + import |
| 6 | MAD#5 | P3 | 虚线框名称是主信息却与数量/到期同用 `onSurfaceVariant`，整卡无层级 | 名称改 `onSurface`，副行/到期/描边保留 `onSurfaceVariant`（对比度均 ≥7:1，纯层级问题） |

## 记录在案、不修

- **动态取色下 High→Highest 仅一档色差，虚线框区分主要靠描边**：MAD 提出「属实可接受」；描边 `onSurfaceVariant` 改 `outlineVariant` 的备选记录在案，装机走查若色差不可辨再调。
- `styles` skill 文件实为 Compose Styles API 迁移指南、非通用风格准则，代理按文件既有风格约定执行——延续既有流程口径即可。

## 通过面摘要（三方一致核实）

- 四组换色与 spec 逐条对应；`neutralFieldColors()` @Composable 工厂、四处调用点（含 ProductionDateField）齐全；无逻辑改动。
- 对比度三主题（浅/深/动态）全部计算过：正文 ≥7:1、虚线 ≥3:1，WCAG AA 富余。
- token 选型符合 M3 规范走向（`surfaceContainerLowest` 正是 material3 1.4 起 OutlinedTextField 新默认底色）；明暗两主题下「输入井」内凹方向一致。
- chips/SegmentedButton 默认配色在新卡片底上依旧成立；全 app 无 `primaryContainer` 旧色残留（除 Theme 定义处，仍被 FAB 等隐式消费）。
