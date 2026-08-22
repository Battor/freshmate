# 需求-6：编辑区配色中性化（2026-08-22）

来源：用户反馈「编辑区域（展示区 + 表单区）的背景颜色还是有点奇怪」。分支 `feature/editing-neutral-colors`（基于 `main`）。

## 背景与问题

编辑态的表单 Card 与「当前操作项目」虚线框底色均为 `primaryContainer`。Android 12+ 动态取色开启时该色跟随壁纸：用户壁纸提取出灰色系，编辑区变成一大片无情绪的灰，与下方固定彩色的过期状态桶并存时观感割裂；且虚线框与表单同色叠同色，层次只靠一圈虚线。这是结构问题——换壁纸只会换成别的怪色，浅色品牌绿下同样存在「大片容器色 + 输入控件透底」的违和。

## 方案（方向 B：保留动态取色，编辑区改用中性 surface 色阶）

App 整体继续跟随壁纸动态取色（顶栏、FAB、列表等不动），仅编辑区三组颜色换 token，全部在 `ItemForm.kt`：

1. **表单 Card**：`containerColor` `primaryContainer` → `surfaceContainerHigh`（中性 surface 族：浅色下灰白、深色下抬高的深灰；随主题换肤但永远中性，不出怪色）。
2. **当前操作项目（虚线框）**：底色 `primaryContainer` → `surfaceContainerHighest`（比表单底深一档，底色差 + 虚线双重区分）；虚线描边与内容文字/图标颜色 `onPrimaryContainer` → `onSurfaceVariant`。
3. **输入框**：四个 `OutlinedTextField`（名称/生产日期/保质期/数量）填 `surfaceContainerLowest`（近白），控件边界清晰、不再透底；chips 保持默认。

**连带**：`ExpiryPreview` 文字色目前写死 `onPrimaryContainer` → 改 `onSurfaceVariant`（随表单底色换，保证对比度）。

品牌回落主题（Android 10–11 的 `LightColors`/`DarkColors`）原未定义 surfaceContainer* 档位，M3 默认回落是偏紫灰、与品牌暖底色相冲突（2026-08-22 三方审查发现，opus+mobile-android-design 一致）——已在 `Color.kt`/`Theme.kt` 从 `LightBackground`/`DarkBackground` 暖调显式派生 Low/Lowest/High/Highest 容器色阶与 `onSurfaceVariant`，两种主题下均成立。

## 不动的部分

表单结构、虚线框实时预览逻辑、自动填充逻辑零改动；顶栏、FAB、列表桶、过期状态色、其余页面全部保持现状；无新增文案；无新增依赖。

## 测试与验证

- 纯换色无新逻辑，不加单测。
- `testDebugUnitTest + assembleDebug + lintDebug` 全绿。
- 装机走查：浅/深两主题 ×（动态取色 + 品牌回落）抽查编辑区——表单底中性、虚线框底深一档可辨、输入框白底清晰、到期预览文字可读；新增表单与编辑表单一致。
