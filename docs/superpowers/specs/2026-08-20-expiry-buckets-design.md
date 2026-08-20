# 需求-3：过期时间分桶列表 + 提醒时点快照（2026-08-20）

来源：`resources/需求-3.txt`。分支 `feature/expiry-buckets`（基于 `feature/freshmate-v1`）。
改动较大的部分（主列表/分组/色板/提醒工具）推倒重写；表单、语音、历史页骨架保留适配。

## 1. 提醒时点：录入时快照存库

**数据模型**：`FoodItem` 新增 `reminderTimes: List<LocalDateTime>`，Room 存 TEXT（ISO 逗号分隔，`Converters` 加一对转换器）。DB version 2→3，不写迁移（开发阶段，靠现有 `fallbackToDestructiveMigration` 清库重建）。

**计算规则**（`ReminderUtils.kt` 重写，纯函数）：

```
候选：expiry − 保质期×(1/3, 1/5, 1/6)  ∪  expiry − (7天, 3天, 1天)
     ↓ 全部向下取整到半小时（沿用现状）
     ↓ 去重（取整后相同）
     ↓ 过滤 ≤ now
     ↓ 相邻间隔 ≤24 小时（含）合并，保留较早者
保底：过滤后为空但 expiry > now 时，追加 expiry 本身（取整半小时）
结果：升序列表，最多 6 个，允许为空（录入时已过期）
```

示例：保质期 30 天的牛奶，候选 `10/7/6/5/3/1 天` → 合并后 `[剩1/3(10天), 7天, 剩1/6(5天), 3天, 1天]` 共 5 个（7 与 6 恰整 24h 合并留 7；7 与 5 间隔 2 天不合并）。
间隔用"≤24h（含）"：7 天与 6 天档经半小时取整后恰为整 24 小时，严格小于会漏合并。

**写入时机**：新增保存、编辑保存时算好连同条目写库；编辑改生产日期/保质期即整体重算覆盖。

**调度器**：`ReminderScheduler.schedule(item)` 直接读 `item.reminderTimes` 设闹钟（删除内部公式计算）；`REMINDER_COUNT` 3→6（`requestCode = itemId*10+index`，index 0..5 位宽仍够）。`rescheduleAll`/开机恢复只读库。临近过期确认对话框（`pendingSave.skippedReminders`）按快照时点数统计。录入时已过期的条目不设提醒（沿用 `scheduleOrCancel` 对称逻辑）。

## 2. 列表分桶与交互

**桶结构**（`MainViewModel` 新纯函数，替换 `groupItems`/`groupKey`/`FoodItemGroup`）：

| 序 | 桶 | 条件（剩余时长） | 头部文案 |
|---|---|---|---|
| 1 | 已过期 | < 0 | 已过期 |
| 2 | ≤1 天 | ≤24h | 1 天内到期 |
| 3 | ≤3 天 | ≤72h | 3 天内到期 |
| 4 | ≤7 天 | ≤168h | 7 天内到期 |
| 5 | ≤14 天 | ≤336h | 14 天内到期 |
| 6 | 更久 | >14 天 | 更久到期 |

空桶不渲染；桶内按到期时间升序（最紧急在前；已过期桶 = 过期最久的在前）。now 用 VM 统一 `nowProvider`（卡片不再各自 `remember { now }`）。桶容器沿用 GroupBox 视觉（浅边框 + 组头），**组头与组空白不可点击**——点卡片 = 编辑，无组级交互。

**LazyColumn 结构**：`[表单（打开时）] → [本次添加区（非空时）] → [六个桶] → [空态]`

- **表单**：顶部内嵌首项，新增/编辑共用；原"目标组/新组"宿主逻辑删除。FabMenu ←/✓、语音、日历等 ItemForm 内容不变。
- **本次添加区**：VM 内存集合 `sessionAddedIds`（保存成功即加入），以"本次添加"组头展示；区内条目不在桶中重复出现。下拉刷新或冷启动（VM 重建）→ 集合清空，条目散入各桶。
- **下拉刷新**：M3 `PullToRefreshBox`（material3 1.3.0，BOM 2024.09.02 已含，ExperimentalMaterial3Api，无需新依赖），唯一动作 = 清空 `sessionAddedIds`。
- **历史页还原**：原"仅组活跃时可还原"限制随组模型消失——随时可还原，恢复后直接落入所属桶。

**删除的旧概念**：`FoodItemGroup`/`groupKey`/`activeGroup`/组续加（`startAddTo`）/空表单点组退出/编辑期他组卡片变淡。组不再有状态，表单开关是全部状态。

## 3. 六档色板与状态体系统一

`ExpiryStatus` 重定义为绝对时间六档（与分桶同阈值）：

```
EXPIRED → DUE_1D(≤1天) → DUE_3D(≤3天) → DUE_7D(≤7天) → DUE_14D(≤14天) → SAFE(>14天)
深红 ─────── 红 ────── 橙 ────── 琥珀 ────── 黄绿 ────── 绿
```

- 颜色收敛到 `StatusColor.kt`：六档 × 双主题（浅色粉彩容器色，深色低饱和容器 + 浅前景），经 `LocalStatusColors`（CompositionLocal）在 `Theme.kt` 随 `colorScheme` 提供（即审查清单 P0-4 修法）。
- 消费方统一：卡片状态色、桶头底色/文字、历史页条目色全读同一来源。卡片右侧文案（"还有 x 到期 / 已过期 x"）不变。
- 桶头用各档容器色，组容器边框统一浅色；`Colors.kt` 死色值随手删（重写文件内自然发生）。

## 4. 范围

- **重写**：`MainScreen.kt`、`MainViewModel.kt`（分组→分桶+会话集合）、`StatusColor.kt`、`ExpiryStatus.kt`、`ReminderUtils.kt`
- **修改**：`FoodItem.kt`（+reminderTimes）、`Converters`/`FoodItemDatabase`（version 3）、`ReminderScheduler.kt`、`ReminderIds.kt`（COUNT=6）、`HistoryScreen.kt`/`HistoryViewModel.kt`（色板统一 + 还原简化）、`FabMenu.kt`（去组参数）、`Theme.kt`（LocalStatusColors）
- **不动**：`ItemForm.kt` 表单内容、语音录入、更新模块、设置页、通知渠道
- **TODO（未来）**：食品搜索功能（"是否已买过"查询场景，本次无新功能）

## 5. 测试与验证

- `ReminderUtilsTest` 重写：合并规则（≤24h 留较早）、保底（全过期未到期→expiry）、短保质期、半小时取整。
- `ExpiryStatusTest` 重写：六档边界 24h/72h/168h/336h。
- `MainViewModelTest` 重写：分桶归属、sessionAddedIds 散入、hasFormContent 保留。
- 验证链：`testDebugUnitTest + assembleDebug` 全绿 → 装机走查（30 天/2 天条目提醒计划、桶归属、本次添加→下拉刷新散入、深浅色六档）→ 实现完成后用 `mobile-android-design` / `edge-to-edge` / `android-intent-security` / `styles` 四个 skill 对新代码重新审查（`docs/reviews/2026-08-20-skill-audit-findings.md` 旧清单仅备用参照）。
