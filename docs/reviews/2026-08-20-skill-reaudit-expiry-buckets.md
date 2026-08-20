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

## 第二批修复（提交 711db22 / d592435，2026-08-20 同日）

用户要求把中低项一并修掉，10 项落地：删除滑动背景接入色板（深色对称）；裸 fontSize → typography（bodyLarge/bodySmall/bodyMedium）；圆角集中 `MaterialTheme.shapes`（AppShapes 8/12/16dp）；pinnedItems/buckets 改存储字段随更新点预算（withDerived）；「本次添加」组头加无障碍「散入各桶」动作；历史页卡片 now 由 VM 统一下发；表单关闭恢复原滚动位置；SmallFAB 48dp 触摸目标；下拉刷新 400ms 可见反馈；requestCode 加 itemId 上界 require。

## 遗留（记录在案）

- 已删条目 id 残留 sessionItemIds：**保留为预期行为**——撤销/还原后重回置顶区有测试锚定（`置顶区条目删除后撤销仍在置顶区`）
- DB 迁移：App 尚未发布，首个版本即 DB v3 全新安装，无需任何迁移脚本；仅在**首次发布后**再改表结构时才需要写迁移（到时 fallbackToDestructiveMigration 须一并移除）
- ItemForm.kt 仍有一处 13sp 裸字号（不在两批修复范围，下轮随手）

## 通过面摘要

- 六档色板双主题对比度达标、`LocalStatusColors` 接线正确、屏幕代码无散落硬编码色
- 旧三大账落地：状态色深色变体（P0-4）、edge-to-edge 三连（P0-1）、主列表空态（P1-11）
- 安全面未被削弱：组件导出最小化、PendingIntent IMMUTABLE+显式、BootReceiver 受保护广播+action 校验、requestCode 扩 index 0..5 无碰撞（数学验证）
- LazyColumn key 稳定、手势正交（下拉刷新×滑动删除）、表单滚动逻辑正确

## 审查分歧记录

mobile-android-design 判定"FAB 无双重 padding"通过，edge-to-edge（专精 skill）判定双计——采信后者并修复；装机走查键盘场景可现场复核。
