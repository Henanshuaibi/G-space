# G space · 0.5

面向学习的 G 系列应用入口。原生 Android、极简浅色界面，数据保存在本机。应用图标和首页 G 标识采用与 G-music 一致的黑底白色风格。

## 试用

点击首页右上角的黑白 G 即可展开菜单，包含「时长权限」「使用说明」「关于 G space」。点击菜单外或按返回可关闭菜单。

安装 `build/G-space-0.5.apk`，启动「G space」。若已安装 G music，首次打开会自动添加它。点击底部「添加应用」搜索并批量添加手机中有启动入口的应用。长按图标直接拖动排序，其他图标会让出位置，松手后保存；停在滚动区域的上、下边缘会持续滚动，便于跨屏排序。拖到底部临时出现的「移除入口」区域后松手并确认，即可移除入口。移除只修改 G space的列表，手机里的应用和数据会保留。拖出网格区域松手、按返回或切到后台会取消未完成的排序。

在 Termux 执行以下命令，将安装包放到手机下载文件夹后，使用文件管理器打开并覆盖安装：

```sh
cp ~/G-space-0.5.apk ~/storage/downloads/
```

首页显示所添加应用的今日使用时长。点击顶部统计卡片，按提示在系统设置中为 G space开启「使用情况访问权限」，返回后自动刷新。可以查看今天、近 7 天以及点击柱图选中的某一天；重新回到应用会刷新列表和统计，在前台每 30 秒刷新一次。

## 时长含义

统计 Android 记录的应用前台使用时间，从桌面或其他入口打开也计入。不把后台播放音乐或 VPN 网络服务时长算作学习时长。总时长是当前添加的应用时长之和，移除入口后该应用也会从合计中移除；重新添加可以重新显示系统仍保留的记录。多窗口中多个应用可能同时处于前台，应用时长之和可能超过实际经过的时间。

详细事件以本地日历午夜分日，并处理跨天、锁屏、关闭屏幕、活动切换、重启及夏令时。Android 仅保留最近几天的详细事件；更早日期使用系统按日汇总并显示 `≈`，日界线可能与本机午夜不同。记录不完整时同样标记 `≈`，没有记录时显示 `—`，不会伪装成零。系统记录可能延迟，不代表实际学习效果。

相关平台说明：[拖放交互](https://developer.android.com/develop/ui/views/touch-and-input/drag-drop/view)、[UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)、[UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)、[应用可见性](https://developer.android.com/training/package-visibility/declaring)。

## 构建

包名 `cn.g.space`，Android 8.0+，版本 `0.5`，独立签名。现有 Termux 环境直接执行：

```sh
sh build.sh
sh tests/check.sh
sh tests/device-check.sh
```

默认从相邻 `../pulse-player/tools/` 读取 `android.jar`、`android-res.jar`、`r8.jar`，也可用 `GSPACE_SDK_DIR` 指定它们所在目录。需要已安装的 aapt2、JDK、zipalign 和 apksigner。

本地签名文件是 `tools/g-space.keystore` 和 `tools/signing-password`，私有备份在 `$HOME/.local/share/gspace-signing/`。可通过 `GSPACE_KEYSTORE` 与 `GSPACE_SIGNING_PASSWORD_FILE` 显式指定备份文件。请保留原密钥以便覆盖更新。构建脚本核对 `signing-certificate.sha256`，缺少或不匹配时停止，不会自动生成新密钥。

## 验证范围

- 拖动检查：隔离预览与已保存顺序、松手提交、取消/过期操作不改顺序、2/3/5 列落点和末行空位、连续边缘滚动及暂停清理。
- 纯 Java 检查：选择列表去重、移动、移除与重新添加；跨日、屏幕锁定、活动重叠、重启边界、运行中会话及夏令时；系统汇总区间过滤。
- Android 运行时检查：真实 JSON 与偏好存储往返、拖动提交/取消后的保存恢复、显式空列表、损坏配置恢复。界面离屏绘制尝试受当前 Termux 进程的系统上下文权限限制，未验证界面渲染。
- APK 检查：独立包名、权限范围、应用可见性声明、没有卸载操作和签名凭据、APK 签名与 zipalign。
- 仍需安装试用确认：当前手机系统的权限设置页面、真实应用启动、厂商使用记录与实际时长、状态栏/键盘/触摸交互，以及真实设备上的拖动、自动滚动和移除区手感。没有连接可用的 ADB 设备，不宣称这些已验证。
