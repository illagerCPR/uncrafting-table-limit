# Uncrafting Table Limit（拆解台限制）

给[暮色森林（The Twilight Forest）](https://www.curseforge.com/minecraft/mc-mods/the-twilight-forest)的**拆解台**加上可配置的使用限制，用于服务器平衡。

暮色森林自带的拆解台允许玩家把物品反向拆解回材料，代价只有经验。本模组提供四种**互斥**的限制方式，通过配置文件**单选**生效；默认启用"全局冷却"，即每次拆解后需要等待一个游戏日。

---

## 支持的版本

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.209 及以上 |
| 暮色森林 | 4.8.3345 及以上 |
| 必需 | 是（暮色森林为前置，必须安装） |

## 安装

1. 安装 Minecraft 1.21.1 对应的 NeoForge。
2. 把 `uncraftinglimit-<版本>.jar` 和暮色森林的 jar 一起放进 `mods/` 文件夹。
3. 启动游戏。配置文件会在首次启动后生成：
   - 单人游戏 / 局域网主机：`.minecraft/config/uncraftinglimit-common.toml`
   - 专用服务器：`<服务器目录>/config/uncraftinglimit-common.toml`

限制在**服务端**判定，客户端无需额外配置；单人游戏改配置后重进世界即可生效。

---

## 限制方式

配置项 `general.mode` 决定当前使用哪一种，**同一时间只有一种生效**。

### 1. `COOLDOWN` —— 全局冷却（默认）

每次成功拆解后，该玩家需要等待 `cooldown.cooldown_ticks` 才能再次拆解。默认 `24000` ticks = 1 个游戏日（20 分钟）。

这个计时是**按玩家**计算的，并且写入存档，退出重进或服务器重启都不会重置。冷却期间打开拆解台界面，剩余时间会**直接显示在界面内**（底部左侧、深红色文字）实时倒数。

### 2. `DISABLED` —— 禁止拆解

玩家仍然可以打开拆解台、预览拆解结果，但**无法取出**任何拆解产物。界面内会常驻显示禁用提示。

如果希望连界面都不允许打开，把 `disabled.block_menu_open` 设为 `true`。

### 3. `DURABILITY` —— 使用耐久

**每一座**拆解台有独立耐久，每次拆解消耗 `durability.durability_per_use` 点：

- 新放置的拆解台耐久为 `durability.max_durability`（默认 1000，即 1000 次拆解）。
- 耐久耗尽后，默认**销毁该拆解台**（`durability.destroy_on_deplete = true`）；设为 `false` 则方块保留但失效。
- 剩余耐久低于 `durability.warn_threshold` 时，使用时会给出提示；打开拆解台界面也会**直接看到剩余耐久次数**（低于阈值或耗尽时变红）。
- 拆掉再放回**不会**重置耐久，耐久跟随方块本身。

### 4. `ITEM_COST` —— 物品代价

每次拆解额外消耗玩家物品栏中的指定物品。在 `item_cost.item_cost_entries` 中填写，格式为 `命名空间:物品:数量`（数量可省略，默认为 1）：

```toml
[item_cost]
	# 任一条目满足即可，默认消耗暮色森林的火炬浆果
	item_cost_entries = ["twilightforest:torchberries:1"]
```

列表为空时，该模式会拒绝所有拆解（等价于配置错误，请在日志中确认）。打开拆解台界面时，界面内会显示下一次拆解的代价（可支付为灰色，不足时为红色）。

---

## 配置参考

```toml
[general]
	# COOLDOWN | DISABLED | DURABILITY | ITEM_COST
	mode = "COOLDOWN"
	# 创造模式玩家是否完全不受限制
	exempt_creative = false
	# 被拒绝时是否给玩家动作栏提示
	notify_player = true
	# 被拒绝时是否播放"否"音效（GUI 打开时比文字更易察觉）
	feedback_sound = true
	# 两条提示之间的最小间隔（ticks），避免刷屏
	feedback_min_interval = 20

[cooldown]
	cooldown_ticks = 24000

[disabled]
	block_menu_open = false

[durability]
	max_durability = 1000
	durability_per_use = 1
	destroy_on_deplete = true
	warn_threshold = 50

[item_cost]
	item_cost_entries = ["twilightforest:torchberries:1"]
```

---

## 常见问题

**能和暮色森林自带的 `disableUncraftingTable` 一起用吗？**
可以，但没必要：暮色森林那个选项是"整个禁用拆解台"，一旦开启，本模组的所有模式都不会有实际作用。本模组存在的意义是提供更细粒度的限制（频率 / 耐久 / 代价），建议保持它关闭。

**改了配置要重启吗？**
重进世界（单人）或重载服务器配置即可生效，不需要重启游戏。

**多人服务器上每个玩家能单独设置吗？**
不能。`mode` 与各项参数是**服务器全局**设置。

**冷却时间能设成"不限次数"吗？**
把 `cooldown.cooldown_ticks` 设为 `0` 即可。

---

## 授权

LGPL-2.1，见 `LICENSE`。
