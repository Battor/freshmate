# 配色改造设计（多巴胺配色落地）

日期：2026-09-26
状态：已与用户逐项确认（浏览器 mock + 终端问答）

> **后续修订（2026-09-26 装机走查后）**：§3 的渐隐宽度定稿为**卡片宽度的 14%**。走查发现
> 渐隐此前**从未生效**——`Brush.horizontalGradient` 色标默认映射整个组件宽度，渐隐段落在
> `drawRect` 之外被整体裁掉，用户看到的一直是 0% 渐隐的硬边（12/14/25% 观感无区别的根因）；
> 修复为显式锚定 `startX/endX` 到绘制矩形。桶头进度条保持删除（以缺口边框取代，走查反馈
> 新增，见下）。走查新增：桶改**缺口边框**（标题嵌在顶部边框缺口、靠右，边框线 = 档位
> accent 45% alpha、标题实色）；"更久到期"卡只显示天数（向下取整）；编辑预览虚线框背景
> 随到期档位容器色；FAB 用品牌主色；应用名按语言（食刻 / FreshMate）。

## 背景与痛点

装机走查反馈四处配色问题：

1. 主页桶头进度条颜色太浅看不清（填充 = 状态容器色粉彩，6dp 高贴近白底）
2. 新增/编辑表单背景"深灰色很奇怪"
3. 编辑态"上滑/下滑"梯形颜色奇怪
4. 主页列表项左侧分类图标看不出是什么颜色

## 根因

**两套颜色系统错位**：

- `MaterialTheme.colorScheme` 在 Android 12+ 走**动态取色**（`dynamicColor = true`），品牌色板（Color.kt 的绿/天蓝/蜜桃与暖调 surface 阶）从未在用户设备上生效。痛点 2（表单卡 `surfaceContainerHigh` 回落壁纸派生灰）与痛点 3（梯形 `primaryContainer` 随壁纸变）由此而来。
- 状态色板（`LocalStatusColors`，固定粉彩）本身没问题，但其低饱和端被借去干了高辨识度的活：痛点 1（进度条填充 = 容器色）、痛点 4（图标 tint = 状态 on 色，颜色跟过期档位走而非跟分类走）。

## 决策（逐项经用户确认）

| # | 问题 | 决策 |
|---|---|---|
| 1 | 动态取色留不留 | **关闭**，全 app 固定品牌配色 |
| 2 | 分类图标颜色 | **深浅主题都按分类配色**；深色用亮而低饱和的变体（拒绝"浅 A 深 B"的语义漂移方案） |
| 3 | 进度条 | **桶头进度条删除**，信息下沉为每张卡片的余量色层 |
| 4 | 余量色层方向 | **油表方向**：剩余时间越多色层越长 |
| 5 | 色层边缘 | 过渡带 = **卡片宽度的 12%** 线性渐隐（手机宽度下验证） |
| 6 | 表单背景 | **随所选分类联动**的极淡色调；编辑既有条目按其分类着色 |
| 7 | 梯形颜色 | **中绿 #4E8B54 + 白字**，深浅主题共用；圆角形状不变 |

## 设计

### 1. 主题层

`Theme.kt`：删除动态取色分支（`dynamicColor` 参数、`Build.VERSION` 判断、`dynamicLight/DarkColorScheme` 调用），深浅品牌方案始终生效。跟随系统的深浅切换保留。

### 2. 分类色系统（新建 `ui/theme/CategoryColors.kt`）

每分类 4 值。表单底深色 = 图标深色 12% 混入 `#242620`（预计算）：

| 分类 | 图标浅 | 图标深 | 表单底浅 | 表单底深 |
|---|---|---|---|---|
| 果蔬 | #2E7D32 | #81C784 | #E3F0DC | #2F392C |
| 肉蛋 | #C62828 | #E57373 | #FAE3DC | #3B2F2C |
| 乳品 | #1E88E5 | #64B5F6 | #E1EDF9 | #2C373A |
| 饮料 | #00ACC1 | #4DD0E1 | #DCF2F5 | #293A37 |
| 零食 | #F57C00 | #FFB74D | #FDE6DC | #3E3725 |
| 主食 | #795548 | #A1887F | #EFE8E4 | #33322B |
| 冷冻 | #78909C | #B0BEC5 | #DFF4F8 | #353834 |
| 调味 | #AFB42B | #DCE775 | #F3F2D6 | #3A3D2A |

```kotlin
fun categoryIconColor(category: Category, darkTheme: Boolean): Color
fun categoryFormColor(category: Category, darkTheme: Boolean): Color
```

`categoryIcon` 的全部消费方（`FoodItemCard`、`HistoryScreen`、`ItemForm` 占位框）统一改用 `categoryIconColor`——图标颜色全 app 一致表达"分类身份"。

### 3. 卡片余量色层（`FoodItemCard`）

纯函数（放 `util`，配单元测试）：

```kotlin
/** 剩余 ÷ 总保质期；过期归 0；总时长 ≤ 0 归 1；结果 clamp [0,1]。 */
fun remainingFraction(
    productionDate: LocalDate?,
    createdAt: LocalDateTime,
    shelfLifeDays: Int,
    now: LocalDateTime,
): Float
```

绘制：卡片背景叠 `Box` 层——填充宽度 = fraction × 卡宽，颜色 = **当前档位 `on` 色 18% alpha**（浅色主题自动加深、深色主题自动提亮，零新增色值，文字对比度不受影响），右缘 12% 卡宽水平线性渐隐。已过期（fraction = 0）无色层。

桶头：`BucketProgressBar`、条宽测量（`rememberTextMeasurer` + `BUCKET_BAR_WIDTH_SCALE`）及相关常量删除，行内只剩右对齐组头文字（颜色仍 `statusColors.on`）。

### 4. 表单背景联动（`ItemForm`）

卡片 `containerColor = categoryFormColor(state.category, darkTheme)`（`isSystemInDarkTheme()` 判定）。输入框维持 `surfaceContainerLowest` 中性填充（浅白/深黑），与任何底色相容。

### 5. 梯形（`EditingPager` / `Color.kt`）

`Color.kt` 新增 `val TrapFill = Color(0xFF4E8B54)`（深浅共用）。`TrapIndicator` 背景 `primaryContainer` → `TrapFill`，文字 → `Color.White`。`TrapezoidShape`（6dp 贝塞尔圆角、10dp 斜边）不动。

## 不动的部分

六档状态色板数值、卡片/桶头结构与交互、滑动删除/还原背景、通知、导航、深浅主题切换机制。

## 测试

- 单元测试：`remainingFraction`（正常、过期归 0、零总时长归 1、边界 clamp）
- 全量：`testDebugUnitTest assembleDebug lintDebug`
- 装机走查：四痛点逐项核对；8 分类 × 深浅主题表单底色；梯形在深浅底上的可读性；深色主题图标变体辨识度
