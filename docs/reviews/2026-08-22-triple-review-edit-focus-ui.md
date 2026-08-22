# 需求-5 实现后三方审查记录（2026-08-22）

审查基线：`git diff main..HEAD`（feature/edit-focus-ui，需求-5：主页编辑聚焦交互 + 桶头进度条）。
审查方式：轻量模式——opus 合并审查 + `mobile-android-design` / `styles` 双 skill 只读并行重审（三代理同时跑，发现合并去重后一次性修复，提交 72278c8）。

## 三方审查修复（提交 72278c8）

| # | 来源 | 严重度 | 问题 | 修法 |
|---|---|---|---|---|
| 1 | opus#1 + MAD#2 | Important/P2 | 遮罩 `detectTapGestures{}` 只吸收点按，拖动穿层：列表可滚、下拉可触发刷新（散桶是真实状态副作用） | 遮罩改 `awaitEachGesture` 消费全部指针事件；`onRefresh` 加 `editing == null` 确定性门禁（双保险） |
| 2 | MAD#1 | P1 | 浅色主题下进度条填充（粉彩容器色）与轨道（容器色 30%）叠 surface 几乎不可见，最紧急桶视觉信号最弱 | 填充改状态深色 `on`（同族深色压得住底）；EXPIRED 例外取饱和容器深红（整条红实心）；轨道 = `on` 20% 透明 |
| 3 | opus#2 + MAD#6 | Important/P3 | 进度条无语义，TalkBack 朗读对窗口刻度无意义的「7%」；M3 默认 gap+尾点圆点噪声 | Row `semantics(mergeDescendants=true)` 合并单焦点 + 指示器 `clearAndSetSemantics{}`；`gapSize=0.dp`、`drawStopIndicator={}` |
| 4 | styles#1+#5 + opus#7 | P2/Minor | `status!!` 非空断言；`14f` 满刻度与枚举重复编码 | 分支改 `if (status != null)` 智能转换；枚举加 `windowProgress`（`PROGRESS_WINDOW_DAYS=14` 具名常量），UI 只消费现值 |
| 5 | styles#4 + MAD#4 | P2/P3 | 魔法浮点数（0.38f/1.5f/0.3f）；scrim 0.38 偏离 M3 令牌 | 具名常量 `EDITING_SCRIM_ALPHA=0.32f`（对齐 M3 模态 scrim）、`BUCKET_BAR_WIDTH_SCALE`、`BUCKET_BAR_TRACK_ALPHA` |
| 6 | MAD#5 + opus#6 | P3/Minor | `textMeasurer.measure` 每次 BucketBox 重组重测 | `remember(header, textStyle)` 缓存 |
| 7 | opus#3 + MAD#3 | Minor/P2 | 遮罩不阻 TalkBack 焦点，「散入各桶」自定义动作编辑期间仍可激活 | 编辑期间 `onHeaderAction` 传 null（不挂 customActions） |
| 8 | MAD#7 | P3 | 编辑目标卡片（enabled=false）仍可被 SwipeToDismissBox 拖出回弹 | FoodItemCard 改 `if (enabled) SwipeToDismissBox{card()} else card()`，禁用态直接渲染 Card |
| 9 | styles#2+#3 | P2 | onDeleteItem 两段 13 行逐字重复；LazyListScope 构建器内过滤链每重组重算且嵌套深 | 提取局部 `onDeleteItem` lambda 与 `visiblePinned`/`visibleBuckets` val（构建器外），退回 `if (isNotEmpty()) item(...)` |
| 10 | styles#6+#7 | P3 | `withAutoProductionDate` 夹在两个 @Composable 之间；chip 里 `quick.value.toString()` 转两次 | 函数移到文件顶部非 composable 区；局部 `val text` 复用 |
| 11 | opus#10 + styles#8 | Minor/P3 | 进度窗口测试复述枚举表（同义反复 change-detector）、块体格式不合邻居 | 改断言不变量：比例 ∈ (0,1]、EXPIRED/SAFE 取满、due 梯子（drop(1)）单调不减 |

## 修复过程中的一次返工

- 首轮验证 `ExpiryStatusTest` 红：单调断言误把 EXPIRED（枚举首位、取满 1.0）算进梯子 → 改 `entries.drop(1)` 后绿。
- ItemForm 曾短暂出现 `withAutoProductionDate` 双份（移动时先加后删顺序失误）→ 编译器以 Conflicting overloads 拦住，删旧位置后绿。

## 记录在案、不修（装机走查确认）

- **深色主题下黑 scrim 32% 的压暗感知**：M3 令牌对齐值，走查第 5/6 项确认「列表变暗」可感知，必要时再调。
- **大字号/英文长文案下进度条宽度**：`Modifier.width` 被父约束钳制自动降级不溢出（文字可能被挤压），走查三语言+大字号确认。
- **TalkBack 焦点仍可进入压暗列表的纯文本节点**：完全语义隔离需 `clearAndSetSemantics` 或文案资源（与「无新增文案」冲突），仅做了组头动作门禁（#7），余下记为已知限制。
- **编辑期间目标条目被外部删除**（当前 UI 无路径）：`editingTarget == null` 时表单静默保留，保存会复活条目（编辑保存回活跃态语义）——复活优于丢失，接受。
- **保质期删空后 ✓ 不消失**：自动填入的生产日期使表单仍「有内容」，符合 hasFormContent 语义，可接受。
- **单位切换不触发自动填充**：spec 定义触发源为「手输/chips」，单位切换不算修改保质期字段，维持。
- 存量问题不动：ItemForm `StringRes` 与 MainViewModel `R` import 错序（配 ktlint 时统一修）。

## 通过面摘要（三方一致）

- 钉顶结构正确：表单 Column + `Box(weight(1f))` 组合、`imePadding` 上移外层、表单超高自身 verticalScroll、Scaffold inset 消费顺序无误；键盘弹出表单可滚至焦点字段。
- `editingTarget` 用计算属性而非 withDerived 派生态是正确决策（startEdit/backToMethodSelection 不经 withDerived，理由经代码核实成立）；测试覆盖 startEdit/放弃/新增三路径。
- 列表过滤边界干净：被编辑条目是置顶区唯一条目时置顶区整体消失、无悬空头；全列表过滤不误触「空列表」文案。
- 自动填充三条件（新增-only、生产日期为空、值>0）实现正确；「0」→「05」序列、清除后重填、编辑旧条目不填均符合 spec。
- 自动填充不改变 ✓ 出现时序（shelfLifeValue 非空已使 hasFormContent=true）。
- 顶栏迁移 M3 标准 TopAppBar 用法正确；遮罩范围（盖列表与刷新指示器、不盖顶栏/FAB）符合设计；scrim 用 `colorScheme.scrim`（黑）而非设计稿 onSurface 是正确实现决策。
- 新增 import 全部按字母序、无残留无用 import；注释风格（需求编号、解释为什么）与工程一致；无新增用户可见文案，三语资源零改动。
- 验证链 `testDebugUnitTest + assembleDebug + lintDebug` 全绿（修复后复跑）。
