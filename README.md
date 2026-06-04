#  包裹库存管理系统 (Barcode Inventory System)

> 一套完整的包裹扫码入库、分拣、出库、签收管理系统，包含 **Android 扫码 App** + **PC 管理服务器**。

---

##  项目结构

```
barcode-inventory-system/
├── BarcodeScannerApp/          # Android 扫码 App（Kotlin + CameraX + ML Kit）
│   ├── app/src/main/java/      # Kotlin 源码
│   ├── app/src/main/res/       # 布局、资源文件
│   └── build.gradle            # Gradle 构建配置
├── BarcodeServer/              # PC 管理服务器（Python Flask）
│   ├── api/                    # REST API 路由
│   ├── db/                     # 数据库操作
│   ├── gui/                    # 桌面 GUI（PyQt5）
│   └── main.py                 # 服务器入口
└── SysMonitor/                 # 系统监控工具（Python）
    ├── monitor.py              # 系统资源监控
    ├── services.py             # 服务管理
    └── widgets.py              # 监控组件
```

---

##  功能特性

###  Android App
| 功能 | 说明 |
|------|------|
|  **扫码入库** | CameraX + ML Kit 高速扫码，支持 EAN-13、Code 128、QR Code 等所有主流格式 |
|  **分拣** | 扫码分拣包裹，自动检查状态防止重复操作 |
|  **出库** | 录入物流单号和收件人，完成出库 |
|  **签收** | 签收包裹，支持异常标记 |
|  **改地址** | 修改包裹地址，记录修改历史 |
|  **记录查询** | 按状态、时间、条码搜索包裹记录 |
|  **数据同步** | 上传本机数据到服务器 / 从服务器拉取数据 |
|  **备份恢复** | 备份/恢复服务器数据 |
|  **手机服务器** | 手机可作为临时服务器（嵌入式 Ktor） |

###  PC 管理服务器
| 功能 | 说明 |
|------|------|
|  **仪表盘** | 实时统计：总包裹数、今日入库、分拣、出库、签收 |
|  **记录管理** | 查看、搜索、编辑、删除包裹记录 |
|  **用户管理** | 添加/删除用户，管理员/操作员角色 |
|  **设备管理** | 查看在线设备，管理已注册设备 |
|  **数据同步** | 接收手机端推送，提供数据拉取 |
|  **备份恢复** | 一键备份/恢复数据库 |
|  **导出** | 导出记录为 JSON 格式 |
|  **系统托盘** | 最小化到系统托盘，后台运行 |

---

##  快速开始

### Android App 构建

```bash
# 环境要求：JDK 21+、Android SDK 34+
cd BarcodeScannerApp
./gradlew assembleDebug

# 安装到手机
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### PC 服务器启动

```bash
# 环境要求：Python 3.8+
cd BarcodeServer
pip install -r requirements.txt

# 启动桌面 GUI
python main.py

# 或仅启动 API 服务器（无 GUI）
python main.py --server
```

---

##  技术栈

| 组件 | 技术 |
|------|------|
| **Android App** | Kotlin, CameraX, ML Kit Barcode Scanning, Ktor, OkHttp |
| **PC 服务器** | Python, Flask, PyQt5, Waitress |
| **数据库** | SQLite |
| **构建工具** | Gradle 8.13, pip |

---

##  许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

---

##  贡献

欢迎提交 Issue 和 Pull Request！
