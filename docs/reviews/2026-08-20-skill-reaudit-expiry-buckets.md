# 需求-3 实现后四 skill 重审记录（2026-08-20）

审查基线：`git diff 459dafd..HEAD`（feature/expiry-buckets，需求-3：过期时间分桶 + 提醒快照）。
审查方式：mobile-android-design / edge-to-edge / android-intent-security / styles 四个 skill 的只读并行审查。旧清单 `2026-08-20-skill-audit-findings.md` 仅作对照，其中 MainScreen/分组相关条目已随重写失效。

## 已修复（提交 08b6bbf）

| # | 来源 | 问题 | 修法 |
|---|---|---|---|
| 1 | edge-to-edge | 键盘弹出内容双计导航栏 inset | `Column.padding(padding).consumeWindowInsets(padding)` |
| 2 | edge-to-edge | FAB 键盘抬升双计导航栏 | `windowInsetsPadding(ime.exclude(navigationBars))` |
| 3 | edge-to-edge | Snackbar 被键盘埋住 | SnackbarHost 同上排除法 |
| 4 | UI | 页面级 now 不随时间刷新，条目跨档后桶不迁移 | `refreshNow()` + ON_RESUME 触发（含测试） |
| 5 | UI | TalkBack「删除」动作绕过 enabled 门禁 | customActions 按 enabled 提供 |
| 6 | styles(中) | Color.kt 五个死色值（Green/Peach/Yellow/Sky/Pink） | 删除 |
| 7 | 安全(低) | 快照异常超长可产生 cancel 清不掉的孤儿闹钟 | `schedule()` 加 `.take(REMINDER_COUNT)` |
| 8 | UI(低) | 历史页对话框残留"分组"字样 | 文案改"过期时间桶" |
| 9 | UI(低) | persist 吞 CancellationException | 补上抛（与 delete/undoDelete 同风格） |
| 10 | styles(低) | `0xFFC62828` 与 ExpiredRed 双写 | 色板引用常量 |

## 遗留未修（下轮候选，均低/打磨级）

- styles：删除滑动背景绕过色板（深色主题不对称，`FoodItemCard` 滑动背景直接用 ExpiredRed）；卡片裸 fontSize 三连在 FoodItemCard/HistoryScreen 重复；16dp/8dp 圆角魔法数未集中（`MaterialTheme.shapes` 未用）
- UI：计算属性（pinnedItems/buckets/hasFormContent）每次重组重算；下拉刷新对 TalkBack/开关控制不可达；历史页卡片仍各自 `remember { now }`；编辑保存后视口停在顶部不回原位；SmallFAB 40dp < 48dp 触摸目标；已删条目 id 残留 sessionItemIds（还原后重回置顶区，影响极小）；下拉刷新指示器恒不显示（散入瞬时完成）
- 安全(记录)：`requestCode` 的 itemId Long→Int 截断理论上可碰撞（个人应用量级达不到）；DB v3 清库重建上线前需换正式迁移

## 通过面摘要

- 六档色板双主题对比度达标、`LocalStatusColors` 接线正确、屏幕代码无散落硬编码色
- 旧三大账落地：状态色深色变体（P0-4）、edge-to-edge 三连（P0-1）、主列表空态（P1-11）
- 安全面未被削弱：组件导出最小化、PendingIntent IMMUTABLE+显式、BootReceiver 受保护广播+action 校验、requestCode 扩 index 0..5 无碰撞（数学验证）
- LazyColumn key 稳定、手势正交（下拉刷新×滑动删除）、表单滚动逻辑正确

## 审查分歧记录

mobile-android-design 判定"FAB 无双重 padding"通过，edge-to-edge（专精 skill）判定双计——采信后者并修复；装机走查键盘场景可现场复核。
