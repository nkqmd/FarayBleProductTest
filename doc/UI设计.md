# Android 产测 App UI 设计

## 1. 文档定位

本文基于当前 `app/` 目录中的实际实现整理，不再描述“计划中的 UI”，而是记录“现在 App 已经是什么样、有哪些交互约束、哪些文案和状态已经落地”。

- 代码基线：`Single-Activity + BottomNavigationView + Fragment + ViewModel + LiveData + Room`
- 业务页：`配置`、`产测`、`结果`
- 登录页单独显示，不进入底部导航容器
- UI 风格以 Material 3 表单、卡片和列表为主，强调稳定和现场可读性

## 2. 当前实现总览

当前 App 已经具备以下 UI 能力：

- 登录页支持本地会话恢复、登录中进度、错误提示。
- 配置页支持工位号/批次号录入、批次摘要下载、MAC 白名单导入、RSSI 阈值调节。
- 产测页支持蓝牙权限申请、启动前校验、实时统计、设备列表颜色状态、停止后自动跳转结果页。
- 结果页支持最近一次会话结果上报，也支持当前批次累计结果上报。
- 应用启动时会恢复已保存的配置和结果；如果上次运行时存在未正常结束的会话，会恢复为已停止会话。

## 3. 全局壳层

## 3.1 页面结构

实际导航结构如下：

```text
登录页
  -> 配置页
  -> 产测页
  -> 结果页
```

- 导航图定义在 `app_nav_graph.xml`。
- 登录成功后固定跳转到配置页。
- 登录页不显示 `Toolbar` 和 `BottomNavigationView`。
- 业务页共享一个 `Toolbar + FragmentContainerView + BottomNavigationView` 容器。

## 3.2 Toolbar 与底部导航

当前壳层行为：

- 标题随目的地切换：`登录 / 配置 / 产测 / 结果`
- 副标题优先显示 `批次 {batchId} | 工位 {factoryId}`，任一缺失时显示 `未选择批次`
- 产测运行中：
  - 底部导航 alpha 降为 `0.55`
  - 所有底部导航项禁用
  - 若用户不在产测页，会被强制切回产测页
- 未登录且当前不在登录页时，会自动回到登录页

## 3.3 启动恢复逻辑

应用启动后会先绑定共享状态，再恢复本地状态：

- 认证状态：尝试读取本地 token；若 access token 过期则尝试 refresh token；refresh token 失效则停留在登录页
- 配置状态：恢复本地保存的 `factoryId`、`lastBatchId`、批次摘要和已导入 MAC 数量
- 结果状态：恢复最近一次已停止会话的统计与上传结果
- 中断恢复：若数据库里仍存在 `RUNNING` 会话，启动时会直接将其改为 `STOPPED`，并在结果页提示恢复成功

## 3.4 当前颜色体系

当前颜色定义集中在 `res/values/colors.xml`：

| 语义 | 背景 | 前景 |
| --- | --- | --- |
| 默认 | `#ECEFF1` | `#37474F` |
| 进行中 | `#E3F2FD` | `#1565C0` |
| 成功 | `#E8F5E9` | `#2E7D32` |
| 失败 | `#FFEBEE` | `#C62828` |
| 非法设备 | `#FFF3E0` | `#EF6C00` |
| 已产测 | `#F3E5F5` | `#6A1B9A` |
| 警告 | `#FFF8E1` | `#F9A825` |
| 禁用 | `#CFD8DC` | `#78909C` |

说明：

- 产测列表项背景色跟随 `DeviceUiStatus` 切换。
- `DUPLICATE_MAC_IN_PROGRESS` 不改变主状态色，只把卡片描边改成警告色，并显示 `重号警告` 标签。

## 4. 登录页

## 4.1 页面目标

- 输入用户名和密码
- 支持直接提交登录
- 支持自动恢复登录
- 失败时在页内显示错误信息

## 4.2 当前布局

登录页是一个居中的单卡片表单，包含：

- 标题：`Android 产测 App`
- 副标题：`登录后进入批次配置、产测与结果页`
- 用户名输入框
- 密码输入框（带密码显隐切换）
- 演示提示文案
- 错误提示区
- 顶部线性进度条
- 登录按钮

当前布局还内置了演示默认值：

- 用户名默认值：`test`
- 密码默认值：`123456`

这属于当前实现中的调试/演示便利行为，不应被视为长期产品要求。

## 4.3 交互规则

- 用户名和密码均非空时，登录按钮才可点击。
- 点击登录后：
  - 输入框禁用
  - 线性进度条显示
  - 按钮不可重复点击
- 密码输入框支持 `IME_ACTION_DONE` 直接提交。
- 登录成功后，由壳层自动导航到配置页。
- 登录失败后保留输入内容，仅更新错误文案。

## 4.4 状态定义

| 状态 | 页面表现 |
| --- | --- |
| `Idle` | 输入框可编辑，按钮按内容是否为空决定可用性 |
| `Authenticating` | 输入框禁用，显示进度条 |
| `Authenticated` | 由 `MainActivity` 导航到配置页 |
| `RefreshingToken` | 不显示独立页态，ViewModel 内部完成恢复 |
| `Error` | 在表单内显示错误文案 |

## 4.5 当前实现备注

- 当前没有单独的“登录过期弹窗”或一次性横幅，过期信息通过页内错误文案体现。
- 登录恢复和普通登录共用 `AuthViewModel`。

## 5. 配置页

## 5.1 页面目标

- 录入工位号和批次号
- 获取批次摘要
- 下载并导入 MAC 白名单
- 展示批次概要
- 提供 RSSI 阈值调节能力

## 5.2 当前布局

配置页由 3 个主要区块组成：

1. 输入与操作卡片
   - `factoryId`
   - `batchId`
   - `确认并加载`
   - 进度条
   - 成功提示
   - 错误提示
2. 执行结果卡片
   - 摘要状态
   - MAC 下载状态
   - 导入数量
   - 最近更新时间
3. 批次摘要卡片
   - 批次号
   - 设备型号
   - 目标数量
   - 固件版本
   - 广播前缀
   - RSSI 范围文本
   - RSSI `SeekBar`

当前布局同样带有默认演示值：

- 工位号：`F01`
- 批次号：`BATCH20260409001`

## 5.3 交互规则

- `factoryId`、`batchId` 非空时，按钮才可用。
- 产测运行中禁止重新加载批次。
- 点击 `确认并加载` 后按以下顺序执行：
  1. 保存工位号到本地
  2. 获取有效 access token
  3. 下载批次摘要
  4. 下载并导入 MAC 白名单
  5. 更新 UI 与共享壳层状态
- `batchId` 仅允许字符集：`A-Z a-z 0-9 . _ -`

## 5.4 页面状态

| 状态 | 页面表现 |
| --- | --- |
| `Idle` | 可输入，可点击加载，摘要卡片可能为空 |
| `Loading` | 输入禁用，按钮禁用，显示线性进度条 |
| `Ready` | 显示 `Batch is ready for production`，允许进入产测 |
| `Error` | 显示错误文案，保留输入值 |

说明：

- 当前实现没有单独的 `ImportingMacList(progress)` 文案状态模型，而是用 `loading + importProgress` 驱动进度。
- 执行状态文案目前以英文为主，例如：
  - `Loading batch summary...`
  - `Downloading MAC list...`
  - `MAC list imported`
  - `Batch summary restored`

## 5.5 RSSI 配置

这是当前实现相对旧版方案新增的一项能力：

- 摘要卡片中提供 `SeekBar`
- 范围约束：`-100 dBm ~ -30 dBm`
- 当前文本格式：`RSSI: 0 dBm ~ -70 dBm` 这一类范围展示
- 用户拖动后会调用 `updateBatchRssiMin` 持久化到批次配置
- 只有批次摘要存在时才允许拖动

## 5.6 恢复行为

配置页会尝试从本地恢复：

- 上次工位号
- 上次批次号
- 批次摘要
- 已导入数量

恢复成功后：

- 摘要状态显示 `Batch summary restored`
- MAC 状态显示 `MAC list restored from local storage`
- 若摘要存在且导入数大于 0，则 `canStartProduction = true`

## 6. 产测页

## 6.1 页面目标

- 启动或停止产测
- 实时显示统计
- 按首次出现顺序显示设备列表
- 在设备项上直接体现状态和警告

## 6.2 当前布局

产测页是单页三段式布局：

1. 顶部按钮区
   - `开始产测`
   - `停止产测`
2. 实时统计区
   - 测试总数
   - 实测数
   - 成功数
   - 失败数
   - 非法数
   - 成功率
3. DUT 列表区
   - 标题
   - `RecyclerView`
   - 空态文案

当前页面没有单独展示队列数、运行中数、扫描开关等字段，虽然这些状态已经存在于 `ProductionUiState` 中。

## 6.3 启动前校验

点击 `开始产测` 时，当前实现会做两层检查：

1. 页面层权限申请
   - `BLUETOOTH_SCAN`
   - `BLUETOOTH_CONNECT`
   - `ACCESS_FINE_LOCATION`
2. 用例层启动校验
   - 已完成批次加载
   - 批次摘要中的蓝牙前缀合法
   - 蓝牙已开启
   - 系统定位已开启

任一步失败，空态区直接显示错误文案。

## 6.4 按钮规则

| 场景 | 开始产测 | 停止产测 |
| --- | --- | --- |
| 未完成配置 | 禁用 | 禁用 |
| 已完成配置，未运行 | 可点击 | 禁用 |
| 运行中 | 禁用 | 可点击 |
| 停止中 | 禁用 | 禁用 |
| 已停止 / 已上报 | 可再次开始新会话 | 禁用 |

实现细节：

- `开始产测` 依赖 `shell.canStartProduction`
- 只要底部导航被锁定，开始按钮也会一起禁用

## 6.5 统计口径

当前代码里的统计口径需要按真实实现理解：

- `successCount`：`PASS`
- `failCount`：`FAIL`
- `invalidCount`：`INVALID_DEVICE`
- `actualCount`：仅统计最终进入 `PASS/FAIL` 的数量
- `successRate`：`successCount / actualCount * 100`
- `totalCount`：
  - 运行中优先取当前可见设备列表长度
  - 同时保证不小于 `actualCount + invalidCount`

因此，`actualCount` 不是“进入 BLE 流程中的数量”，而是当前实现里“最终形成 PASS/FAIL 记录的数量”。

## 6.6 空态与错误态

空态文案按以下优先级生成：

1. 若存在错误：直接显示错误文案
2. 若还不能开始产测：`请先在配置页完成批次加载`
3. 若正在运行：
   - 有前缀时：`Scanning BLE devices with prefix "{prefix}"...`
   - 无前缀时：`Scanning BLE devices...`
4. 其他情况：`尚未开始产测`

## 6.7 DUT 列表规则

设备列表使用 `ListAdapter + stableIds`，以 `sequenceNo` 作为稳定主键。

当前列表项包含：

- 第一行：MAC + 状态
- 第二行：`RSSI {value} dBm | {advName}`
- 第三行：可选警告标签 `重号警告`

展示规则：

- 新设备追加到列表中
- `sequenceNo` 不变时只刷新该项
- 不按状态重排
- 列表支持垂直滚动

## 6.8 状态颜色映射

| `DeviceUiStatus` | 颜色 | 文案 |
| --- | --- | --- |
| `DISCOVERED` | 默认色 | 已发现 |
| `INVALID_DEVICE` | 非法色 | 非法设备 |
| `ALREADY_TESTED` | 已产测色 | 已产测 |
| `QUEUED` | 进行中色 | 待测 |
| `CONNECTING` | 进行中色 | 连接中 |
| `SUBSCRIBING` | 进行中色 | 订阅中 |
| `SENDING` | 进行中色 | 发送中 |
| `WAITING_NOTIFY` | 进行中色 | 等待通知 |
| `DISCONNECTING` | 进行中色 | 断开中 |
| `PASS` | 成功色 | 通过 |
| `FAIL` | 失败色 | 失败 |

补充规则：

- `DUPLICATE_MAC_IN_PROGRESS` 时描边变黄
- 警告文本单独显示在卡片底部

## 6.9 停止后的跳转

当前实现有一个旧文档中未体现的行为：

- 用户点击 `停止产测` 后，如果本次会话已经形成测试结果（`actualCount > 0 || invalidCount > 0`）
- 待会话状态变为 `STOPPED` 时，会自动从产测页跳到结果页

若没有任何有效结果，则停留在产测页。

## 7. 结果页

## 7.1 页面目标

结果页现在有两套上报能力：

1. 最近一次会话结果上报
2. 当前批次累计结果上报

它不再只是“显示最近一个停止会话”。

## 7.2 当前布局

结果页由以下区块组成：

- 空态文案
- 会话摘要卡片
  - 会话号
  - 批次号
- 会话统计卡片
  - 目标数量
  - 实测数
  - 成功数
  - 失败数
  - 非法数
  - 成功率
- `结果上报` 按钮
- 会话上报反馈卡片
  - 上报状态
  - 消息
  - Upload ID
  - 是否重复
- `批次累计结果上报` 按钮
- 批次累计上报反馈卡片
  - 上报状态
  - 消息
  - Upload ID
  - 是否重复

## 7.3 可见性规则

- 没有会话时：
  - 显示 `暂无可上报结果`
  - 隐藏会话卡片、统计卡片、会话上报按钮和会话反馈卡片
- 只要存在 `batchId`：
  - 批次累计上报按钮和反馈卡片就会显示
- 会话和批次两个上传区块彼此独立

## 7.4 会话结果上报

会话上报规则：

- 只有 `uploadEnabled = true` 且存在 `sessionId` 时可点击
- 点击后状态依次变为：
  - `Uploading`
  - `Upload success` 或 `Duplicate upload` 或 `Upload failed`
- 成功后若当前产测页状态还是 `STOPPED`，会额外把产测状态推进到 `UPLOADED`

反馈区字段：

- `上报状态`
- `message`
- `uploadId`
- `重复上传`

颜色规则：

- 上传中：蓝色
- 重复上传：黄色
- 有 `uploadId` 且非重复：绿色
- 其他：默认灰色

## 7.5 批次累计结果上报

这是当前实现相对旧版文档最大的变化之一。

规则如下：

- 需要存在 `batchId` 和 `factoryId`
- 会话不能处于 `RUNNING / STOPPING`
- 需要至少有一次已结束会话
- 需要当前批次下存在本地测试结果（`actualCount > 0 || invalidCount > 0`）

上传前会先构建 `batch_result.json`，然后上传该文件。

反馈区同样展示：

- 上报状态
- message
- Upload ID
- 是否重复

默认占位文案是：`停止产测后可在此页上传当前批次累计结果`

## 7.6 恢复行为

结果页每次 `onResume` 都会重新执行恢复逻辑，因此：

- 最近一次会话统计会被刷新
- 最近一次会话上传记录会被回显
- 最近一次批次累计上传记录也会被回显

恢复后常见状态文案：

- `Pending upload`
- `Upload success`
- `Duplicate upload`

## 8. 当前实现中的异常与恢复口径

## 8.1 认证相关

- 登录失败时不弹窗，直接显示页内错误
- 配置下载或结果上报遇到认证错误时，会清空认证态并返回登录流程

## 8.2 产测相关

- 权限拒绝、蓝牙关闭、定位关闭、扫描异常，都会走统一错误发布逻辑
- 若错误发生在运行中，会主动停会话并解锁底部导航
- 出错后结果页的“会话上报”可能仍可用，但“批次累计上报”会被关闭

## 8.3 中断恢复

- App 重启后，未正常结束的运行中会话会被视为“中断后恢复的已停止会话”
- 结果页会保留统计并提示可以继续上传

## 9. 页面实现映射

| 页面 | Fragment | ViewModel | 布局 |
| --- | --- | --- | --- |
| 登录页 | `LoginFragment` | `AuthViewModel` | `fragment_login.xml` |
| 配置页 | `ConfigFragment` | `ConfigViewModel` | `fragment_config.xml` |
| 产测页 | `ProductionFragment` | `ProductionViewModel` | `fragment_production.xml` |
| 结果页 | `ResultFragment` | `ResultViewModel` | `fragment_result.xml` |

共享壳层：

- `MainActivity`
- `SharedSessionViewModel`
- `ShellUiState`

## 10. 与旧版方案稿的主要差异

为避免后续继续按旧方案理解 UI，这里列出当前最关键的差异：

1. 配置页已经新增 RSSI 滑杆，并且会持久化到批次配置。
2. 登录页、配置页当前自带演示默认值，不是纯空白表单。
3. 产测页当前没有单独显示“批次/工位摘要区块”，这些信息主要在全局 Toolbar 副标题中展示。
4. `actualCount` 的实际含义是最终 `PASS + FAIL`，不是“所有进入测试流程的设备数”。
5. 停止产测后若已有结果，会自动跳结果页。
6. 结果页已经支持两种上传：单会话上传和批次累计上传。
7. 启动时会自动恢复中断会话、配置状态和上传记录，这些都已经属于当前 UI 行为的一部分。

## 11. 文档维护建议

后续如果继续迭代 UI，建议优先同步以下文件，再回写本文：

- `MainActivity.kt`
- `ui/shared/*`
- `ui/login/*`
- `ui/config/*`
- `ui/production/*`
- `ui/result/*`
- `res/layout/*.xml`
- `res/values/strings.xml`

尤其要留意“状态口径”和“按钮可用条件”，这些地方最容易出现代码和文档再次漂移。
