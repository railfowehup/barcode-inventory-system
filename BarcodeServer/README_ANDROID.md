Android APK 构建说明
=====================

目标：将 `public` 中的手机端页面打包成原生 Android 应用（APK）。

前提
- 在本机已安装 Node.js、npm。
- 已安装 Java JDK（11+）并配置 `JAVA_HOME`。
- 已安装 Android SDK / Android Studio，能使用 `sdkmanager` 和 `gradle`。

步骤（在项目根目录执行）：

1. 安装 Capacitor CLI（本地或全局）

```powershell
npm install --save-dev @capacitor/cli @capacitor/core
```

2. 初始化 Capacitor（仅首次）

```powershell
npm run cap:init
```

3. 添加 Android 平台（仅首次）

```powershell
npm run cap:add-android
```

4. 每次构建前，把 web 资源复制到原生项目

```powershell
npm run cap:copy
npm run cap:sync
```

5. 在 Android Studio 中打开项目并构建（推荐）
- 打开 `android` 目录：

```powershell
npm run cap:open-android
```

- 在 Android Studio 中：
  - 选择 `Build > Generate Signed Bundle / APK...` 来生成签名的 release APK（需要签名密钥）。
  - 或直接运行 `Run` 在设备上调试。

6. 命令行构建（可选）

Windows:
```powershell
cd android
gradlew.bat assembleRelease
```

macOS/Linux:
```bash
cd android
./gradlew assembleRelease
```

生成文件位于 `android/app/build/outputs/apk/release/`。

签名与发布
- 若需要发布到 Google Play，请使用签名的 APK/AAB，并按 Play Console 要求上传。

注意事项
- 如果你的 web 页面使用了 `http://<ip>:3000` 之类的地址（非 https），在 Android 9+ 可能需要允许明文流量或使用 https。请在 `android/app/src/main/AndroidManifest.xml` 或 network security config 中允许。Capacitor 文档有详细说明。
- 若遇到权限或相机无法访问，请在 Android 项目中配置相应的权限并在运行时请求。

需要我做的额外工作（可选）
- 我可以为你自动创建 `android` 平台（运行 `npx cap add android`）并把 `public` 资源复制进去（你需要在本机运行或允许我运行远程构建）。
- 我可以添加一个 GitHub Actions workflow 来在云端构建 APK（需要你在 GitHub 上启用仓库并配置签名密钥）。

