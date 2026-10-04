# 1.3 滚轮、删除和单次考试验证记录

日期：2026-10-04，Asia/Shanghai。下方保留 1.2 / 1.1 / 1.0 历史记录。

## 当前交付包

- `dist/grandeTrend-v1.3.apk`，版本 1.3.0 / versionCode 4，包名 `com.grandetrend.app`，Android 10 及以上。
- 2,567,465 字节；release 优化，同一测试签名。SHA-256：`1c894b7c0a3263c6c3fc87d00ea83e77ecf3bf4598f893ace0399cf92b50301c`。
- 签名证书 SHA-256：`cd203bdc9556b02c0c21b06e5b32de93f8aa85fdaa876292a607d375c72b4362`。数据库版本 1，JSON 备份格式 1，旧交付包均保留。

## 验证结果

| 项目 | 结果与证据（位于 `verification/v1.3/`） |
| --- | --- |
| 构建与静态检查 | Debug、Release、测试 APK 构建成功；17 项业务单元测试通过（原有 11 项 + 单次考试/删除/选科规则 6 项）。Lint 零错误、5 项原有横屏/KTX 提示。`checks-final.txt`、`TEST-*.xml`、`lint.txt`、`package.txt`。 |
| 完整回归 | 常规 914 × 411 dp 下 21 项仪器测试通过，覆盖原有 16 项与新增 5 项流程，包括备份恢复、事务回滚及导出。`all-large.txt`。 |
| 最终常规交互 | 紧凑雷达布局及输入法处理调整后，5 项新流程复验通过，`feature-final-large.txt`；`large/` 为最终常规截图。 |
| 短横屏 | 640 × 288 dp，5 项新流程全部通过。发现九科外围文字拥挤后，改为科目名称、右侧得分率与更大的多边形，并再次通过 5 项流程。`feature-small.txt`、`small/`。 |
| 120% 字体 | 780 × 360 dp、120% 字体，紧凑布局的 5 项新流程全部通过。`feature-font-120.txt`、`font-120/`。 |
| 窗口尺寸适配 | 最后改为实际窗口尺寸，短屏/120% 字体分别重跑九科、零分、超分和删除范围端点流程，均通过。`window-small.txt`、`window-font-120.txt`。 |
| 滚动实时刷新 | 保持手指按下并移动滚轮，在松手之前断言标题与当前成绩已切科；总分启动状态和点击总分行为正确。`large/wheel-during-scroll.png`。 |
| 两处选科关系 | 新增默认使用常用科目，临时加入化学后主页面能查看该科；再次新增仍只使用原常用科目，不回写偏好。历史科目也可查看和导出。 |
| 单次考试 | 当前考试名称、精确原始分数、历史满分、总分/总排名、裸分/赋分切换、前后边界、进入指定考试修改均正确。`large/radar-raw.png`、`radar-awarded.png`。 |
| 雷达数据语义 | 不同满分归一化；缺失赋分留空、断开、无填充；真实零分为中心点；超满分扩展上限；少于三科用条形展示；极小非零值不被归一化计算舍成零。`large/radar-missing.png`、`radar-nine-over-max.png`、`two-subjects-missing.png` 和单元测试。 |
| 删除考试 | 取消保持全部记录与偏好一致；确认删除目标及其关联成绩，其他记录和偏好保持原值，SQL 查询确认无孤立成绩。修改中打开输入法后也能删除；最后一场删除后显示空状态，重启不恢复已删记录。最后一场删除时也恢复全部考试筛选，`delete-final.txt`。`large/delete-confirm.png`、`after-last-delete.png`。 |
| 删除自定义范围端点 | 删范围末端后，保留区间内剩余记录，图表与摘要正常；只有一场的区间删除后回到全部。单元测试与九科流程通过。`large/range-after-delete.png`。 |
| 实际 1.2 → 1.3 覆盖安装 | 旧版准备两场同日考试、4 条成绩和设置，直接覆盖安装最终优化包并重启；逐值内容与私有标记保留。`update-install.txt`、`update-seed.txt`、`update-verify.txt`。 |
| 实际 1.0 → 1.3 覆盖安装 | 首版直接升级最终版，使用同样数据校验。`update-from-v1.txt`、`from-v1/update-seed.txt`、`from-v1/update-verify.txt`。 |
| 数据一致性 | 覆盖前/后稳定内容摘要均为 `68ec8929f478e8f583cc6dcaef80ba634283ca91070ee8c7159e9ac3330e0fc6`，涵盖小数、零分、NULL、官方总分、历史满分、备注、同日顺序、常用科目和空指标集合。 |
| 最终优化包原生交互 | 从公开录入控件创建六科记录，总分 622.125 / 646.25 正确；两种雷达视图及删除取消/确认通过。逻辑收尾后的最终 APK 在学期筛选下删除最后一场，恢复全部考试和空状态；`release-ui.txt`、`release-radar-raw.png`、`release-last-delete-all.png`、`release-empty.png`，`crash-log.txt` 为空。 |
| 视觉检查 | `design-qa.md` 记录实际正常/短屏/大字体截图及发现的标签拥挤修复，最终无剩余 P0/P1/P2。 |

升级与仪器测试仅在专用 API 35 模拟器 `emulator-5580` 进行；测试代码清空测试安装的数据，不用于真实用户手机。升级脚本初始卸载只用于准备旧版样本，实际两版覆盖过程中没有卸载、清空或重建数据库。交付 APK 不含测试成绩，首次安装为空。

正常更新直接覆盖安装，**不要先卸载或清空应用数据**。未在真实手机或 API 29 设备运行；系统输入法和文件选择界面沿用已有行为。

---

# 1.2 界面更新验证记录

日期：2026-10-04，Asia/Shanghai。下方保留 1.1 / 1.0 历史记录。

## 当前交付包

- `dist/grandeTrend-v1.2.apk`，版本 1.2.0 / versionCode 3，包名 `com.grandetrend.app`，Android 10 及以上。
- 2,551,081 字节；release 优化，同一测试签名。SHA-256：`554bae38687f719be3598dc91a90d7f94d5b54bd1f34c08352e1d69f16db0f63`。
- 签名证书 SHA-256：`cd203bdc9556b02c0c21b06e5b32de93f8aa85fdaa876292a607d375c72b4362`，与旧版及固定签名校验一致，`verification/v1.2/signature.txt`。
- 仅重做录入界面；数据库版本 1、JSON 备份格式 1。原有两个安装包未覆盖，均保留用于升级验证。

## 验证结果

| 项目 | 结果与证据（位于 `verification/v1.2/`） |
| --- | --- |
| 构建和静态检查 | Debug、Release、测试 APK 与独立升级工具构建成功；11 项业务单元测试通过。Lint 零错误、5 项原有横屏/KTX 写法提示。`checks.txt`、`checks-final.txt`、`unit-tests.xml`、`package.txt`。 |
| 完整回归 | 914 × 411 dp 下 16 项仪器测试通过：原有 13 项加 3 项编辑测试；包含完整备份往返、合并、回滚与图表导出。`all-large.txt`。 |
| 紧凑布局复验 | 按用户反馈压缩控件和间距后，正常横屏 7 项相关流程通过；最后收紧选科和日期后，4 项相关流程再次通过。`compact-large.txt`、`compact-final-large.txt`。常规尺寸可同时看到四项考试信息、九个选科选项、六科成绩，`large/editor-empty.png`。 |
| 短横屏 | 640 × 288 dp 下 7 项录入/选科/日期/超分/修改及原有图表流程全部通过，`compact-small.txt`、`small/`。 |
| 120% 字体 | 780 × 360 dp、120% 字体下 4 项录入与新编辑流程全部通过，`compact-font-120.txt`、`font-120/`。 |
| 修改和考试切换 | 自定义考试列表切换旧记录，修改备注后 ID/顺序/历史科目/历史满分/原始值/空值/官方总分和排名保留；其他考试不变、不新增记录。 |
| 超分校验 | 输入 151.125、满分 150 时须明确确认；返回核对不写入数据库，再确认后原值完整保存。 |
| 日期边界 | 月末自动限制到当月有效日期；2024-01-31 切换为 2024-02-29，再改年为 2023-02-28，保存后日期正确。 |
| 实际 1.1 → 1.2 覆盖安装 | 两个原交付优化 APK，旧版保存两场同日考试、4 条科目成绩和偏好后，直接覆盖安装新版并重启，全部记录和私有标记保留。`update-install.txt`、`update-seed.txt`、`update-verify.txt`。 |
| 实际 1.0 → 1.2 覆盖安装 | 首版直接升级到最终版也通过同样的逐值校验。`update-from-v1.txt`、`from-v1/update-seed.txt`、`from-v1/update-verify.txt`。 |
| 数据库完全一致 | 两条升级路径的覆盖前/后稳定摘要均为 `68ec8929f478e8f583cc6dcaef80ba634283ca91070ee8c7159e9ac3330e0fc6`，包含零分、NULL、精确小数、历史满分、官方总分、排名、备注、同日顺序、隐藏科目、选科与空指标集合，数据库仍为版本 1。 |
| 最终优化包界面 | 实际保留的历史考试与空数据库均打开新编辑页成功，`release-editor-existing.png`、`release-editor.png`；`crash-log.txt` 为空。 |
| 视觉检查 | `design-qa.md` 为 `final result: passed`，旧/新界面全屏与局部对照已查看，无剩余 P0/P1/P2。`comparison-full.png`、`comparison-fields.png`。 |

升级验证只在 `emulator-5580` 专用模拟器进行；脚本限制为 Android 模拟器，最初卸载仅用于准备旧版测试安装，两版覆盖过程中没有卸载、清空、复制或重建数据库。检查后已清理该模拟器的测试成绩，交付 APK 首次安装仍为空数据。

正常更新直接覆盖安装，**不要先卸载或清空应用数据**。本次未运行真实手机或 Android 10 设备；截图与运行测试来自 API 35 模拟器。

---

# 1.1 更新验证记录

日期：2026-10-04，Asia/Shanghai。下方保留 1.0 首版的历史记录。

## 当前交付包

- `dist/grandeTrend-v1.1.apk`，版本 1.1.0 / versionCode 2，包名 `com.grandetrend.app`。
- 2,567,465 字节（约 2.5 MB），Android 10 及以上；release 优化，保留首版测试签名。
- SHA-256：`44c49364d16e180be4a897d1c15fe3a17788524e0357c92b0a4ce650cd95d2d1`。
- APK v3 签名验证通过，签名证书 SHA-256 与首版及打包脚本固定摘要一致。
- 数据库结构与版本仍为 1；本次无需改表，不删除或重建数据库。

## 验证结果

| 项目 | 结果与证据 |
| --- | --- |
| 构建与静态检查 | Debug、Release、仪器测试 APK、独立升级验证 APK 构建成功；Lint 零错误，5 个提示为横屏需求和 KTX 写法建议。`verification/v1.1/checks.txt`、`package.txt`。 |
| 业务规则 | 11 项单元测试全部通过；精确小数、空值/零值、总分、排名、满分与范围等原有规则保留。 |
| 正常横屏回归 | 914 × 411 dp 下 13 项仪器测试全部通过，包括原有 6 项流程与新增 7 项备份测试。`verification/v1.1/all-tests-large.txt`。 |
| 短横屏回归 | 640 × 288 dp 下原有录入/图表/导出及新备份界面的 6 项流程通过。`verification/v1.1/ui-tests-small.txt`。 |
| 最终预览布局 | 调整预览优先显示后，正常/短横屏分别重跑 2 项备份流程，全部通过。数量与两项恢复选择在短屏首屏可见。`backup-final-large.txt`、`backup-final-small.txt` 及两张 `backup-preview-*.png`。 |
| 全量备份往返 | 所有考试、历史隐藏科目、历史满分、精确小数、空值、排名、ID/日期/顺序/备注、官方总分、选科与空指标集合保持一致。空数据备份也可往返。 |
| 合并与重复导入 | 默认保留本机重复 ID 和设置；显式选择后才更新重复 ID/恢复设置；本机其他考试不删除；重复导入不会增加重复 ID。 |
| 无效文件与失败回滚 | 损坏/未知版本/错误字段/重复 ID/非法排名/无效指标/超限文件拒绝读取。注入中途 SQLite 写入失败与导入前备份失败，原记录和偏好全部保留。 |
| 最终数据保护检查 | 增加非法 UTF-8 拒绝与自动快照读回核对后，重新运行全部 7 项备份数据/界面测试，全部通过；`backup-final-data.txt`、`checks-final.txt`。 |
| 实际 1.0 → 1.1 覆盖安装 | 使用原交付的两个优化安装包。先在 1.0 保存两场同日考试、4 条科目成绩、选科和指标设置，再 `adb install -r` 安装 1.1 并重新启动。未在两版之间卸载、清空或复制数据库；全部数据库内容摘要与应用私有标记保持不变。`update-seed.txt`、`update-verify.txt`、`update-install.txt`。 |
| 数据库逐字段一致 | 覆盖前后考试、成绩、偏好表的稳定序列摘要均为 `68ec8929f478e8f583cc6dcaef80ba634283ca91070ee8c7159e9ac3330e0fc6`；数据库版本仍为 1。独立验证程序仅用 Android 框架 API，可验证压缩后的旧安装包，不依赖旧包中被改名的应用/Kotlin 类。 |
| 实际系统文件界面 | 在最终优化包通过系统 CreateDocument 将 JSON 保存到 Downloads，清空专用模拟器的测试数据，再用 OpenDocument 选取外部文件、预览并恢复；再次导出的所有考试字段和偏好与原文件完全一致。`release-saf.txt`、`release-saf-before.json`、`release-saf-after.json`、`release-saf-preview.png`、`release-saf-success.png`。 |
| 签名误换保护 | 临时使用不同测试密钥，脚本拒绝产出不兼容的更新包，已交付包摘要未改变。`signing-guard.txt`。 |
| 后续未知迁移 | 无迁移规则时显式报错，原成绩保留；不会悄悄升级版本号或删库。 |

## 验证边界与交付说明

运行设备为 Android API 35 专用模拟器，实机和 Android 10/API 29 尚未实际运行。系统文件提供方、厂商安装器与挖孔仍需在实际手机确认。所有测试记录只在模拟器/测试代码/验证文件中存在，交付安装包首次安装仍为空数据。

正常更新直接覆盖安装。卸载或清空数据会删除本机成绩和自动快照；用户主动保存到外部位置的 JSON 可用于恢复。本版不提供账号或云同步，也不提供 CSV/Excel 通用表格导入。

---

# 首版验证记录

日期：2026-10-04，Asia/Shanghai。

## 交付包

- 文件：`dist/grandeTrend-v1.apk`。
- 应用：成绩趋势，`com.grandetrend.app`，版本 1.0.0 / versionCode 1。
- 大小：2,534,697 字节（约 2.5 MB）。
- Android：minSdk 29 / Android 10，targetSdk 35。
- 构建：release 优化与 R8 压缩，使用本机 Android 测试签名；APK v3 签名校验通过。
- SHA-256：`3417db975f7b95025c02406b708dc26a48f53c46b735f279cc1eee72525c655a`。
- 首次数据为空，测试记录仅存在于仪器测试与截图中，不进入安装包。

## 验证结果

| 项目 | 结果与证据 |
| --- | --- |
| Debug / Release 构建 | `assembleDebug`、`assembleRelease`、仪器测试 APK 构建全部成功。 |
| 业务规则测试 | 11 项全部通过，`verification/unit-tests.xml`。覆盖精确小数、零/空值、赋分缺失、官方覆盖、历史满分、单科唯一排名、反向排名轴、稳定排序、筛选、缩放/拖动边界及录入校验。 |
| 914 × 411 dp 横屏 | 6 项模拟器流程测试通过，`verification/large-tests.txt`。 |
| 640 × 288 dp 横屏 | 相同 6 项流程测试通过，`verification/small-tests.txt`。软键盘下当前字段完整可见，完成键恢复保存操作，紧凑图表轴刻度无重叠。 |
| 780 × 360 dp / 120% 字体 | 图表选中、单科滚轮、指标切换、批量导出和系统分享通过，`verification/font-tests.txt`。 |
| 最终排名标签 | 避开成绩线的定位修复完成，视图操作与导出测试通过，`verification/chart-tests.txt`。 |
| 优化包实际安装 | 最终 `dist/grandeTrend-v1.apk` 在 API 35 模拟器安装、横屏启动成功，初始为空。 |
| 优化包持久化 | 实际操作最终包录入 `ReleaseSmoke` 与官方总分 `123.25`，保存后强制停止并重新启动，名称和精确数值仍存在。证据：`verification/release-smoke.txt`、`verification/release-persistence.png`。随后清除测试数据并恢复空首页。 |
| 图片保存 | 每科独立 PNG，至少 2400 像素宽；通过 MediaStore 写入后再次解码读取，黑底、尺寸、独立 URI 均正确。数学与化学的示例图在 `verification/large/export-0.png`、`verification/large/export-1.png`。 |
| 系统分享 | 成功打开包含两张图片的系统分享面板，`verification/large/share-chooser.png`；未选择收件人或发送图片。 |
| 静态检查 | `lintDebug` 和 release 的 `lintVitalRelease` 通过；零错误。四个提示为需求明确的固定横屏，以及 SQLite / Bitmap / Canvas 的 KTX 写法建议，不影响功能。 |
| 视觉检查 | `design-qa.md` 为 `final result: passed`。参考与实际原生截图进行了全屏及局部同屏对照。 |

## 六项仪器测试内容

1. 首次空数据、新增小数/零分、修改历史满分、原 ID 更新、不重复插入、重建页面后仍保留成绩。
2. 总分/单科选中、早期无赋分、独立指标保存、学科滚轮、两个科目的图片导出、系统分享面板。
3. 36 场考试的双指缩放、左右拖动、恢复全部、学期筛选，以及导出可见范围选项。
4. 自由选择历史/政治等学科、逐次参考科目调整、赋分字段的适用范围、日期滚轮保存。
5. SQLite 关闭/重开、空值/精确小数/历史满分与偏好的完整往返保存、修改保留记录 ID。
6. 批量图片独立保存、可读取的媒体 URI、PNG 解码及图片尺寸检查。

## 原生截图

- 首次启动：`verification/release-empty.png`。
- 总览：`verification/large/overview.png`。
- 数学：`verification/large/math.png`。
- 化学：`verification/large/chemistry.png`。
- 录入：`verification/large/editor.png`。
- 日期：`verification/large/date-wheel.png`。
- 缩放：`verification/large/zoomed.png`。
- 导出完成：`verification/large/export-success.png`。
- 短横屏输入：`verification/small/editor-keyboard.png`。
- 120% 字体：`verification/font-120/overview.png`。
- 对照：`verification/comparison-final.png`、`verification/comparison-header.png`、`verification/comparison-chart.png`。

## 验证边界

实机运行尚未验证。本次运行使用 Android API 35 / x86_64 模拟器；Android 10 的兼容性由 minSdk 与 Lint 静态检查覆盖，尚未运行 API 29 设备。不同厂商的挖孔、手势导航、相册和分享目标应在实际手机上继续试用。

本版没有账号、云同步、数据备份/恢复和删除考试。卸载应用或清空应用数据会移除本机成绩；导出的图片仍按系统相册规则保存。

超长历史导出受可用内存限制，超过上限会明确提示缩小考试范围，不静默遗漏数据。视觉检查是内部检查，用户的最终视觉验收仍以安装后的体验为准。
