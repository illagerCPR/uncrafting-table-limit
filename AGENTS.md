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

# 运行 GameTest / 客户端（完整解锁链，四个参数缺一不可）
python3 tools/neoforge_mirror_proxy.py 8903 &          # 本地代理（镜像+本地缓存+种子目录）
./gradlew -PofflineDeps=true -PmcLibsFallback=true -PneoforgeProxy=http://127.0.0.1:8903/releases runGameTestServer
```

- `-PofflineDeps=true`：TF 从 `libs/`（gitignored）取，不走 CurseMaven。CI 上不要加这个参数。
- `-PmcLibsFallback=true`：应用 `gradle/mc-libs-fallback.gradle`（用 `python3 tools/generate_mc_libs_fallback.py` 重新生成）。CI 上不要加。
- `-PneoforgeProxy=...`：NeoForge 官方仓库**整体**由本地代理替代（不再并列官方源）。CI 上不要加。
- `-PuseQluMirror`：把 qlu 镜像加进仓库列表（默认关闭，见第四节陷阱）。

### M0 验收结果（2026-10-13 回填）

- `runGameTestServer`：**All 1 required tests passed**；模组列表含 Minecraft / NeoForge / RDPMouse / TF 4.8.3345 / uncraftinglimit 0.1.0；`require=1` 下 Mixin 配置加载无应用错误（探针触发归 M1 的真实交互测试）。
- 日志仅两条无害项：首启生成 `server.properties` 的 NoSuchFile、FML VersionChecker 更新检查被反代 MITM 的 PKIX 失败。
- `build` 产出 `uncraftinglimit-0.1.0.jar`。

---

## 三、已核对的硬事实（证据：发布版 jar + 上游源码）

### 3.1 暮色森林（发布版 4.8.3345）

- 发布版类名/签名与 GitHub `1.21.1` 分支源码一致（`javap -p` 逐类核对）。
- `twilightforest:uncrafting_table` → `twilightforest.block.UncraftingTableBlock extends Block`，**没有 BlockEntity**。
- `twilightforest:uncrafting` → `twilightforest.inventory.UncraftingMenu extends RecipeBookMenu`，工厂 `UncraftingMenu::fromNetwork`。
- 槽位布局（56 槽）：`0` = tinkerInput；`1` = `UncraftingResultSlot`（出口）；`2–10` = 内部 `UncraftingContainer`（`UncraftingSlot` 包装，`getContainerSlot()` 为 `300000+i`）；`11–19` = `assemblyMatrix`（`AssemblySlot`）；`20–46` 主背包；`47–55` 快捷栏。
- `clicked(int,int,ClickType,Player)` 是**所有取出动作的唯一必经点**；`UncraftingResultSlot#onTake` 是内部类，**不触发任何 NeoForge 事件** → 限制必须用 Mixin。
- **槽位重定向陷阱**：`clicked` 开头若玩家点的是**空的 assemblyMatrix 槽**且手上无物，会执行 `slotNum -= 9`，把点击重定向到内部拆解格。因此判定"是否在从拆解格取物"必须同时覆盖重定向前后的两种槽位身份（`slots.get(slotNum).container` 与 `slots.get(slotNum - 9).container`）。
- `clicked` 里另有 4 处 `return` 早退：重铸经验不足 / `disableUncraftingOnly` 拦截 / 拆解经验不足 / 产物被 `isMarked` 标记。
- **`TFConfig.disableUncrafting`（配置名 `disableUncrafting`）在源码中从未被读取**——只有声明、初始化与网络同步三处。TF 实际只有 `disableEntireTable` 一个粗粒度开关。
- TF **不使用 Mixin**：自定义转换走独立库 `twilightforest-asm`（`FMLModType: LIBRARY` + `ICoreMod`），10 个 transformer 全部针对原版类；jar 内无 mixin 配置，`accesstransformer.cfg` 未触碰拆解台相关类。
- TF 的 NeoForge 要求 `[21.1.209,)`，许可证 LGPL-2.1。

### 3.2 NeoForge 21.1.248（`javap` 核对）

- `BlockEntityTypeAddBlocksEvent`（mod bus 事件）：`modify(BlockEntityType<?>, Block...)` —— 给已注册方块追加 BE 类型的正规入口（耐久模式要用）。
- `PlayerInteractEvent$RightClickBlock`：可取消，`setUseBlock(TriState)` / `getCancellationResult()`。
- `EventBusSubscriber` 在 `net.neoforged.fml.common`（**不在** neoforge jar 里，在 `fancymodloader/loader`），属性 `value()` / `modid()` / `bus()`，`Bus` 枚举只有 `GAME` / `MOD` 且默认 `GAME`。
- `ModConfigSpec.Builder#defineEnum(String, V)` 存在（`EnumValue`）；`defineListAllowEmpty(String, List, Predicate)` 存在。
- `IBlockExtension` **没有** `canPlayerBreakBlockInPosition` 之类的方法，别凭记忆写。

### 3.3 Mixin

- NeoForge 自带 Mixin（`org.spongepowered:mixin`），**无需新增依赖**。
- 目标类在 `twilightforest` 包（非 MC 包），`@Mixin` 必须 `remap = false`。
- `@Shadow` 可访问 `UncraftingMenu` 的 `private final` 字段（`uncraftingMatrix` / `level` / `player` / `tinkerResult` / `positionData`）；`assemblyMatrix` 与 `tinkerInput` 是 `public final`，可直接用。
- 本工程未配置 refmap / 注解处理器 —— 若 Mixin 静默不生效，先怀疑这里（见第五节验收纪律）。

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
9. **`minecraft-dependencies` 变体选择落空是隐形大坑**：本机环境下 Gradle 给 `net.neoforged:minecraft-dependencies` 选中了无依赖的 `platformDependencies` 变体——表现是三层连锁：①`compileClasspath` 缺 `com.mojang:*`（报"无法访问 Message/authlib"）；②运行 `-cp` 里也缺；③**MDG 的 legacyClasspath 文件里没有 MC 游戏库**，游戏层报 `NoClassDefFoundError: com/mojang/serialization/DynamicOps`。解法 = `-PmcLibsFallback`（显式声明游戏库 + 挂钩 `write*LegacyClasspath` 任务补写）。
10. **legacyClasspath 文件是每行一个路径**，不是冒号拼接——按冒号写FML 一条都读不进去（文件格式错了不报错，静默回到缺类崩溃）。
11. **`DevLaunch` 只在官方 maven 有**（GitHub 仓库无 1.0.2 tag、镜像/Central 全无）：从 GitHub 克隆源码用 JDK21 `javac --release 8` 手编译单文件 `Main.java` + 手写 POM，放进代理的种子目录（**点分组布局** `.gradle-cache-seed/net.neoforged/DevLaunch/1.0.2/<hash>/`，代理按 hash 子目录扫描）。
12. **`NeoForge.EVENT_BUS.register(this)` 在类里没有任何 `@SubscribeEvent` 方法时直接抛 IllegalArgumentException**——M0 主类只注册 mod bus 监听即可，GAME bus 事件等真正需要时用独立的 `@EventBusSubscriber` 类。

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
