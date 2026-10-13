# AGENTS.md — Uncrafting Table Limit

Minecraft **1.21.1 / NeoForge 21.1.248** 附属模组，前置**暮色森林（The Twilight Forest）4.8.3345**，modid `uncraftinglimit`。

给暮色森林的拆解台加互斥的可配置使用限制（全局冷却 / 禁止拆解 / 使用耐久 / 物品代价）。需求与决策见 `docs/01-可行性分析.md`，批次计划见 `docs/02-开发计划.md`。

> 环境与工具链层面的通用经验（WSL2 MTU、反代、JDK、javap 核对技巧、GameTest 纪律）见 `~/.dsh/MCDEVWARNS.md`，此处不重复。

---

## 一、本机环境现状（2026-10-12 实测）

| 项 | 现状 |
| --- | --- |
| Java | PATH 无 `java`；JDK 21 在 `~/.gradle/jdks/jdk-21.0.12.1+1`，构建前必须 `export JAVA_HOME=...` |
| Gradle | wrapper 8.8（发行版已缓存） |
| `maven.neoforged.net` | **本机完全不可达**（TCP 连接被 CDN 边缘重置，浏览器同样失败，非证书问题） |
| 可用镜像 | `https://mirrors4.qlu.edu.cn/bmclapi` —— 是**通用 Maven Central 代理**，也带部分 NeoForge 树；但**不完整**（缺 `neoform-runtime` 等，且大文件偶发 404） |
| Mojang 库 | `https://libraries.minecraft.net` 可达（`com.mojang:*` 只能从这里取） |
| CurseMaven / ForgeCDN | 可达（TF jar 两条路径 SHA1 一致） |
| 参考项目 | `~/projects/the_truth_mod`（同一 NeoForge 版本，1.21.1 硬事实沉淀丰富） |

---

## 二、构建与运行命令（本机）

```bash
export JAVA_HOME=/home/illager_aris/.gradle/jdks/jdk-21.0.12.1+1

# 常规构建（离线依赖，TF 从 libs/ 取）
./gradlew -PofflineDeps=true build

# 运行 GameTest / 客户端（离线解锁链）
python3 tools/neoforge_mirror_proxy.py 8903 &          # 本地代理（镜像+本地缓存+种子目录）
./gradlew -PofflineDeps=true -PneoforgeProxy=http://127.0.0.1:8903/releases runGameTestServer
```

- `-PofflineDeps=true`：TF 从 `libs/`（gitignored）取，不走 CurseMaven。CI 上不要加这个参数。
- `-PneoforgeProxy=...`：NeoForge 官方仓库**整体**由本地代理替代（不再并列官方源）。CI 上不要加。
- `-PmcLibsFallback=true`：**M1 起不再需要**（vendored `.module` 让真实依赖图恢复生效，见第四节陷阱 13/14），保留作应急。CI 上不要加。
- `-PuseQluMirror`：把 qlu 镜像加进仓库列表（默认关闭，见第四节陷阱）。

### M0 验收结果（2026-10-13 回填）

- `runGameTestServer`：**All 1 required tests passed**；模组列表含 Minecraft / NeoForge / RDPMouse / TF 4.8.3345 / uncraftinglimit 0.1.0；`require=1` 下 Mixin 配置加载无应用错误（探针触发归 M1 的真实交互测试）。
- 日志仅两条无害项：首启生成 `server.properties` 的 NoSuchFile、FML VersionChecker 更新检查被反代 MITM 的 PKIX 失败。
- `build` 产出 `uncraftinglimit-0.1.0.jar`。

### M1 验收结果（2026-10-13 回填）

- `runGameTestServer`：**All 6 required tests passed**（smoke + G1 开菜单/56 槽布局 + G2 拆解预览 + G3 首拆成功→立刻再拆被拒 + G4 冷却过期可拆并刷新 + G8 创造豁免）。G3 的"拒绝+不刷新时间戳"与时间戳写入本身构成 Mixin 生效的行为学证明。
- 异常日志扫描：仅剩 VersionChecker PKIX 一项已知无害。
- `runClient` 人工验收（动作栏文案与手感）**待用户进行**。
- `build` 产出 jar；本地链路已简化为两个 `-P` 参数（mcLibsFallback 不再需要）。

### M2 验收结果（2026-10-13 回填）

- `runGameTestServer`：**All 9 required tests passed**（M1 六项 + G5 禁止模式拒取/界面仍可开 + 重铸路径在"冷却中"与 DISABLED 两种状态下均不受影响的显式回归 + `block_menu_open` 经 RightClickBlock 事件 `setUseBlock(FALSE)` 拒绝交互）。
- 异常日志扫描：仅剩 VersionChecker PKIX 已知无害项。
- `runClient` 人工验收（提示文案与手感）**待用户进行**。
- CI：M1 批次（`2d5e614`）已绿（冷缓存验证 vendored `.module` 修复有效）。

### M3 验收结果（2026-10-13 回填）

- `runGameTestServer`：**All 14 required tests passed**（M1/M2 九项 + BE 织入断言 `getBlockEntity(pos) != null` + G6 逐次扣减 + G7 耗尽销毁（destroy_on_deplete=true）+ disable 模式耗尽后拒取 + 耐久随掉落物往返（挖掉带组件 → 放回恢复））。
- 异常日志扫描：仅剩 VersionChecker PKIX 已知无害项。
- `runClient` 人工验收（提示文案与手感）**待用户进行**。
- 实现要点：自有 `BlockEntityType` 在 Builder 里直接声明 TF 方块（`BlockEntityTypeAddBlocksEvent` 不需要——那是给既有类型追加方块的）；`UncraftingTableBlockMixin implements EntityBlock` + 三个复制方法（newBlockEntity / setPlacedBy / playerDestroy）；耐久走 DataComponentType 在物品↔BE 之间往返；耗尽 `destroyBlock(pos, false, null, 512)` 不掉落。

### M4 验收结果（2026-10-13 回填）

- `runGameTestServer`：**All 19 required tests passed**（+ 默认配置 `twilightforest:torchberries:1` 解析验证、有料放行并扣减、缺料/数量不足拒绝、创造豁免）。
- 异常日志扫描：仅剩 VersionChecker PKIX 已知无害项。
- 实现要点：`ItemCostPolicy` 解析 `namespace:item[:count]`（坏条目跳过+一次性 WARN）；**任一**条目可付即放行（与 M0 既定配置注释一致）；HEAD 判付、RETURN 成功后扣减；扣减只遍历玩家背包 36 格（不含光标/盔甲/副手）；空列表=模式不可用并拒一切拆解。
- 决策 #4 核对完成：`TFItems.TORCHBERRIES` → 注册名 `twilightforest:torchberries`。

### M5 验收结果（2026-10-13 回填）

- 版本 `0.1.0 → 1.0.0`（gradle.properties 唯一来源）；`CHANGELOG.md` 建立；README 复查为纯用户向、无需改动。
- 1.0.0 下本地全量复跑：`build` 产出 `uncraftinglimit-1.0.0.jar`；`runGameTestServer` **All 19 required tests passed**、异常扫描仅剩已知无害项。
- 发布提交 `08721a5` CI 绿（冷缓存全流程：build + runGameTestServer）。
- tag `v1.0.0` GPG 签名（密钥指纹核对无误）并推送；GitHub Release 已发布并附 `uncraftinglimit-1.0.0.jar`：<https://github.com/illagerCPR/uncrafting-table-limit/releases/tag/v1.0.0>。
- `runClient` 人工验收（动作栏文案与手感）**仍待用户进行**。
- **1.0.1（2026-10-13）**：真机测试发现许可协议元数据错误——`LICENSE` 文件实为 **LGPL-2.1**，而 `mod_license` 被误写成 GPL-3.0（M0 计划阶段误记所致，docs/01/02 已勘误）。修正后升版 1.0.1 发布：提交 `c2bff22` CI 绿，tag `v1.0.1` 签名，[Release](https://github.com/illagerCPR/uncrafting-table-limit/releases/tag/v1.0.1) 附 jar。教训：**外部输入的仓库事实（如 LICENSE）要在落文档前实读文件核对**。
- **1.1.0（2026-10-13）**：针对真机反馈"动作栏提示在 GUI 下不显眼"，实现 ①拒绝音效（`feedback_sound`，默认开，与 `notify_player` 独立共享限流）②**GUI 内冷却倒计时**——TF `UncraftingScreen.renderLabels` TAIL 注入，在 TF 自家警告行（`imageHeight - 96 + 2`，深红）绘制；冷却时间戳经附件 `sync((holder, player) -> holder == player, ByteBufCodecs.VAR_LONG)` 推送到客户端（setData 字节码确认自动触发），客户端用自己的 gameTime 实时倒数，无逐 tick 包。客户端 mixin 走 mixins.json 的 `client` 段（专用服务器不加载）；GUI 尺寸经 `AbstractContainerScreenAccessor`（imageWidth/imageHeight 是继承字段，@Shadow 不可用）。提交 `e3801ad` CI 绿，tag `v1.1.0`，[Release](https://github.com/illagerCPR/uncrafting-table-limit/releases/tag/v1.1.0) 附 jar。`DISABLED`/`DURABILITY`/`ITEM_COST` 的 GUI 内文字未做（音效+动作栏已够用），留作后续候选。
- **1.2.0（2026-10-13）**：真机截图确认 1.1.0 的 GUI 文字与原版"物品栏"标签**叠印**（drawString 不清除背景字形，"更长会盖住"的假设不成立）——修复 = 绘制前 `GuiGraphics.fill(4, y-1, imageWidth-4, y+9, 0xFFC6C6C6)` 擦除该行。同时 GUI 状态行覆盖全部四模式：DISABLED 常驻禁用提示；DURABILITY 显示当前台剩余耐久（BE 经 `getUpdateTag`=saveWithoutMetadata + `sendBlockUpdated(UPDATE_CLIENTS)` 推送，客户端默认 onDataPacket 自动应用；表位置由客户端 `PlayerInteractEvent.RightClickBlock` 记录——客户端菜单经 fromNetwork 构造，positionData 是 NULL 拿不到 pos）；ITEM_COST 显示代价（客户端本地判付，可支付灰色/不足深红）。提交 `14b9210` CI 绿，tag `v1.2.0`，[Release](https://github.com/illagerCPR/uncrafting-table-limit/releases/tag/v1.2.0) 附 jar。GameTest 20/20（新增 updateTag 载荷断言）。

---

## 三、已核对的硬事实（证据：发布版 jar + 上游源码）

### 3.1 暮色森林（发布版 4.8.3345）

- 发布版类名/签名与 GitHub `1.21.1` 分支源码一致（`javap -p` 逐类核对）。
- `twilightforest:uncrafting_table` → `twilightforest.block.UncraftingTableBlock extends Block`，**没有 BlockEntity**。
- `twilightforest:uncrafting` → `twilightforest.inventory.UncraftingMenu extends RecipeBookMenu`，工厂 `UncraftingMenu::fromNetwork`。
- 槽位布局（56 槽）：`0` = tinkerInput；`1` = `UncraftingResultSlot`（出口）；`2–10` = 内部 `UncraftingContainer`（`UncraftingSlot` 包装，容器内槽位即 `0–8`；**拆解格在 GUI 的 x 坐标是 `300000+i`，即屏幕外不可点**）；`11–19` = `assemblyMatrix`（`AssemblySlot`，纯 `Slot`，重铸路径完全不受限）；`20–46` 主背包；`47–55` 快捷栏。
- `clicked(int,int,ClickType,Player)` 是**所有取出动作的唯一必经点**；`UncraftingResultSlot#onTake` 是内部类，**不触发任何 NeoForge 事件** → 限制必须用 Mixin。
- **槽位重定向（精确条件，M1 源码实读）**：`slotNum > 0 && slots.get(slotNum).container == assemblyMatrix && player.containerMenu.getCarried().isEmpty() && !slots.get(slotNum).hasItem()` 成立时，若再满足 `assemblyMatrix.isEmpty() && (clickType != SWAP || 热键栏 mouseButton 槽为空)` 则 `slotNum -= 9`。拆解格唯一合法入口就是这条重定向（直接点击 2–10 只可能是改过的客户端）。限制逻辑必须逐行复刻，漏一条即漏拦截。
- **一次重定向取物 = 一次完整拆解（M1 关键发现）**：`UncraftingSlot#onTake` 会 ①按整次拆解扣一次经验 `uncraftingCost`；②把剩余产物**复制**进 assemblyMatrix（此后 assemblyMatrix 非空，重定向条件不再成立）；③从 tinkerInput 扣 `numberOfInputItems` 个输入。所以冷却的"一次拆解"天然对应一次重定向取物，无需会话追踪。
- **成功检测陷阱**：`UncraftingInputContainer#removeItem` 会回调 `slotsChanged(tinkerInput)` → `slotsChanged` 第一分支**清空并重灌整个拆解格**。因此"取物成功"不能用 HEAD/RETURN 对比拆解格数量（取完立即恢复满格）——必须对比 **tinkerInput 数量递减**（onTake 必扣输入；TF 自身的拒绝路径不碰输入）。
- `clicked` 里另有 4 处 `return` 早退：重铸经验不足 / `disableUncraftingOnly` 拦截 / 拆解经验不足 / 产物被 `isMarked` 标记。
- **`TFConfig.disableUncrafting`（配置名 `disableUncrafting`）在源码中从未被读取**——只有声明、初始化与网络同步三处。TF 实际只有 `disableEntireTable` 一个粗粒度开关。
- TF **不使用 Mixin**：自定义转换走独立库 `twilightforest-asm`（`FMLModType: LIBRARY` + `ICoreMod`），10 个 transformer 全部针对原版类；jar 内无 mixin 配置，`accesstransformer.cfg` 未触碰拆解台相关类。
- TF 的 NeoForge 要求 `[21.1.209,)`，许可证 LGPL-2.1。

### 3.2 NeoForge 21.1.248（`javap` 核对）

- `BlockEntityTypeAddBlocksEvent`（mod bus 事件）：`modify(BlockEntityType<?>, Block...)` —— 给已注册方块追加 BE 类型的正规入口（耐久模式要用）。
- `PlayerInteractEvent$RightClickBlock`：可取消，`setUseBlock(TriState)` / `getUseBlock()`；**拒绝交互用 `TriState.FALSE`**（没有 `DENY` 常量；TriState = TRUE/DEFAULT/FALSE）。
- `EventBusSubscriber` 在 `net.neoforged.fml.common`（**不在** neoforge jar 里，在 `fancymodloader/loader`），属性 `value()` / `modid()` / `bus()`；**21.1.248 里 `bus` 属性已标记 @Deprecated**（GAME 是默认值，直接写 `@EventBusSubscriber(modid=...)` 即可）。
- `ModConfigSpec.Builder#defineEnum(String, V)` 存在（`EnumValue`）；`defineListAllowEmpty(String, List, Predicate)` 存在。
- `ModConfigSpec.ConfigValue#set(T)` 运行时改值可用（GameTest 里切换配置用，记得恢复）。
- Data Attachment：`AttachmentType.Builder#serialize(Codec)` / `#copyOnDeath()` 均存在；`NeoForgeRegistries.Keys.ATTACHMENT_TYPES` + `DeferredRegister.create(ResourceKey, modid)` 注册；`IAttachmentHolder#getData(Supplier<AttachmentType<T>>)` 默认方法可直接用。
- **`FakePlayer.openMenu(MenuProvider, Consumer)` 被重写为无条件 no-op**（返回 empty 什么都不做）——GameTest 里不能让假玩家"打开"菜单，应直接 `new UncraftingMenu(...)`（与 `UncraftingTableBlock#getMenuProvider` 的 lambda 同路径）再手动 `player.containerMenu = menu`。
- `FakePlayerFactory.getMinecraft(ServerLevel)` 签名不涉及 authlib（可直接引用）；返回**共享实例**，测试间必须清理菜单/背包/经验/能力。
- `AbstractContainerMenu.slots` 是 `public final NonNullList<Slot>`（父类公开字段，`getSlot(int)` 也是 public）——mixin 里用 `(AbstractContainerMenu)(Object)this` 转型即可访问，无需 accessor mixin。
- `Player` 没有 `setExperienceLevels`，只有 `giveExperienceLevels(int)`（字段 `experienceLevel` 本身是 public）。
- `DeferredRegister.DataComponents`（`createDataComponents` 的返回类型）**是子类**——`registerComponentType` 只在子类上，字段别用 `DeferredRegister<DataComponentType<?>>` 父类类型接。
- 数据组件（DataComponentType）注册：`builder.persistent(Codec)` + `networkSynchronized(ByteBufCodecs.VAR_INT)` + `build()`（`net.minecraft.core.component.DataComponentType$Builder`）。
- 物品↔BE 组件传递链（1.21.1 实测）：**放置侧** `BlockItem.place` 会调用 `Block#setPlacedBy(Level, BlockPos, BlockState, LivingEntity, ItemStack)`（Block 默认空实现，覆盖后从 ItemStack 读组件写进 BE）；**挖掘侧** `Block#playerDestroy` 原版体 = `awardStat(BLOCK_MINED)` + `causeFoodExhaustion(0.005F)` + `dropResources(...)`——要改掉落物就复制这个方法体并用 `Block.getDrops(state, serverLevel, pos, be, entity, tool)` + `Block.popResource` 自己发。BE 侧桥接用 `applyImplicitComponents(DataComponentInput)`（读）与 `collectImplicitComponents(Builder)`（写，供创造拾取）；`BlockEntity#applyComponentsFromItemStack` 可直接测试往返。
- **自有 BlockEntityType 不需要 `BlockEntityTypeAddBlocksEvent`**：`BlockEntityType.Builder.of(supplier, blocks...).build(null)` 直接声明合法方块；该事件是给"别人的类型"追加方块用的（注册键 `Registries.BLOCK_ENTITY_TYPE`）。
- `BlockEntity` 的 `loadWithComponents` 会先 `loadAdditional` 再应用组件——字段初始化顺序注意；`Level.getBlockEntity(pos)` 是 IMMEDIATE，**EntityBlock 的 BE 会按需懒创建**（老存档已放置的方块无需迁移）。
- `Level.destroyBlock` 只有 4 参重载 `(pos, drop, entity, recursionLeft)`（原版爆炸用 512）。
- `IBlockExtension` **没有** `canPlayerBreakBlockInPosition` 之类的方法，别凭记忆写。

### 3.3 Mixin

- NeoForge 自带 Mixin（`org.spongepowered:mixin`），**无需新增依赖**。
- 目标类在 `twilightforest` 包（非 MC 包），`@Mixin` 必须 `remap = false`。
- `@Shadow` 可访问 `UncraftingMenu` 的 `private final` 字段（`uncraftingMatrix` / `level` / `player` / `tinkerResult` / `positionData`）；`assemblyMatrix` 与 `tinkerInput` 是 `public final`，可直接用。
- 本工程未配置 refmap / 注解处理器 —— 若 Mixin 静默不生效，先怀疑这里（见第五节验收纪律）。
- **Mixin 可给目标类"加接口+加方法"**：`@Mixin(TF 类) implements EntityBlock`（vanilla 接口，无需 remap）+ 在 mixin 里写**不加 @Unique 的 public 方法**（逐字复制进目标类）。只有接口里的方法能标 `@Override`。M3 用它把纯 Block 变成 EntityBlock 并覆盖 newBlockEntity/setPlacedBy/playerDestroy。
- **@Inject 不能作用于目标类"继承来的"方法**（如 Block.playerDestroy 对 UncraftingTableBlock 是继承的，注入会找不到方法）——只能用"复制方法覆盖"的路子。

---

## 四、依赖与构建陷阱（踩过的坑）

1. **`maven.neoforged.net` 不可达会同时打死构建与运行**：ModDevGradle 把部分工具链仓库（NeoForge userdev、NeoForm runtime、installertools、neoform zip）硬编码指向该主机，项目级 `repositories {}` 覆盖不到它们。
   - 解法分两层：仓库声明集中到 `settings.gradle` 的 `dependencyResolutionManagement`（`PREFER_SETTINGS`），并用 `tools/neoforge_mirror_proxy.py` 把 NeoForge 路径代理到镜像 + 本地 Gradle 缓存。
2. **`PREFER_PROJECT` 会让 MDG 硬编码的官方源生效**（`PREFER_SETTINGS` 才能接管）；但改成 `PREFER_SETTINGS` 后必须把 MDG 需要的仓库（`libraries.minecraft.net`、Maven Central）**自己在 settings 里补全**，否则 `com.mojang:*` 解析不到。
3. **`content {}` 过滤不是可有可无的**：Gradle 在某个仓库拿到 **404 就认为"该模块不存在"**，不会继续尝试后面的仓库。所以任何"不完整"的仓库（镜像、本地代理）都必须用 `includeGroupByRegex` 限定范围，否则会把 `com.mojang`、`cpw.mods` 等其它仓库的构件"吃掉"。
4. **qlu 镜像不完整且不稳定**：`neoform-runtime` 无、`neoform-*.zip` 偶发 404；因此它默认**不启用**（`-PuseQluMirror` 才加），NeoForge 路径一律走本地代理（代理会兜底读 `~/.gradle/caches/modules-2/files-2.1`，并能为只有 `.module` 没有 `.pom` 的模块合成 POM）。
5. **Parchment 坐标要用带补丁号的 MC 版本**：`org.parchmentmc.data:parchment-1.21.1:2024.11.17`（不是 `parchment-1.21`）。
6. **`processResources` 的 `expand` 是全量模板替换**：`neoforge.mods.toml` 里用到的每个占位符都必须在 `expanded` 映射里，否则 `MissingPropertyException`。
7. **编译期不要碰 `com.mojang.authlib`**（如 `Player#getGameProfile()`）——该包不在 NeoForge 编译类路径上，用 `player.getName().getString()` 代替。
8. **GameTest 模板必须是二进制 `.nbt`**：`tools/make_empty_structure.py` 生成（默认 5×5×3 → `src/main/resources/data/uncraftinglimit/structure/smoke.nbt`）；`.snbt` 只在 IDE 目录生效且丢命名空间。
9. **`minecraft-dependencies` 变体选择落空是隐形大坑（M1 已查明根因）**：当 Gradle 拿不到该构件的真实 `.module`（见陷阱 13）时，会退化到只有空 `platformDependencies` 变体的假 POM（M0 时代由本地代理合成）——三层连锁：①`compileClasspath` 缺 `com.mojang:*`（报"无法访问 Message/authlib"）；②运行 `-cp` 里也缺；③MDG 的 legacyClasspath 文件缺 MC 游戏库，游戏层报 `NoClassDefFoundError`。M1 起由 vendored `.module`（陷阱 13）根治，`-PmcLibsFallback`（显式声明游戏库 + 挂钩 `write*LegacyClasspath` 任务补写）降级为应急手段。
10. **legacyClasspath 文件是每行一个路径**，不是冒号拼接——按冒号写FML 一条都读不进去（文件格式错了不报错，静默回到缺类崩溃）。
11. **`DevLaunch` 只在官方 maven 有**（GitHub 仓库无 1.0.2 tag、镜像/Central 全无）：从 GitHub 克隆源码用 JDK21 `javac --release 8` 手编译单文件 `Main.java` + 手写 POM，放进代理的种子目录（**点分组布局** `.gradle-cache-seed/net.neoforged/DevLaunch/1.0.2/<hash>/`，代理按 hash 子目录扫描）。
12. **`NeoForge.EVENT_BUS.register(this)` 在类里没有任何 `@SubscribeEvent` 方法时直接抛 IllegalArgumentException**——M0 主类只注册 mod bus 监听即可，GAME bus 事件等真正需要时用独立的 `@EventBusSubscriber` 类。
13. **`net.neoforged:minecraft-dependencies:1.21.1` 已从官方 maven 消失（2026-10-13 实锤）**：官方源与 `neoforged.forgecdn.net` 镜像上 `.module`/`.pom` 双双 404，而 `neoforge`/`neoform` 照常 200——全球冷缓存 NeoForge 1.21.1 项目都会死于 `Could not find net.neoforged:minecraft-dependencies`。解法：真品 `.module` **内嵌进仓库** `gradle/repo/`（151KB），settings 里以 `includeModule('net.neoforged','minecraft-dependencies')` + `metadataSources { gradleMetadata() }` 的 file 仓库供出（见 `gradle/repo/README.md`）。**升级 MC/NeoForge 前必须从热缓存重新内嵌对应版本**。
14. **诊断 CI 依赖故障的对照法**：①用 `neoforged.forgecdn.net`（官方 CDN 直连镜像）做可用性对照，判断"网络不可达"还是"构件真的没了"；②反编译 MDG 的 `RepositoriesPlugin`（javap -c）看它自己给内部配置建了什么形状的仓库——PREFER_SETTINGS 接管仓库后，内部配置的解析形状必须照抄。CI 首次失败时"只搜 .pom 不搜 .module"是那次运行的表象，不必纠结复现。

---

## 五、验收纪律

1. **Mixin 生效必须显式证明**：跑 `runGameTestServer` / `runClient` 后，日志里必须能看到 Mixin 应用记录与探针日志。不接受"没报错就是生效了"。
2. **GameTest 全绿 ≠ 无异常**：跑完必须 grep 日志 `exception` / `ERROR` / `错误`。
3. **热路径 fail-open**：`clicked` 是每次点击都会走的热路径，策略代码异常一律**放行**并限流记日志，绝不因限制逻辑让玩家崩溃。
4. **不静默删除玩家物品**：耐久耗尽的销毁行为必须提前提示。
5. **签名先于代码**：动 Mixin 目标前用 `javap -cp <TF 发布版 jar>` 复核。
6. 每批次完成 → 推送 → 等 CI 绿 → **停下等用户指令**。

---

## 六、Git 纪律

- commit / tag 全局 GPG 签名（指纹 `D2E7DBACB6E233780954B2DEF09BEB5215872019`），签名后必须检查签名信息。
- 推送使用 GitHub 隐私邮箱 `63698328+illagerCPR@users.noreply.github.com`。
- 版本号唯一来源：`gradle.properties` 的 `mod_version`。
