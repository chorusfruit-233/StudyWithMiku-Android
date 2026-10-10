# 使用固定密钥构建 Release APK

正式包使用用户提供的 `StudyWithMiku.jks`，别名为 `StudyWithMiku`。密钥文件与密码不进入 Git；工作流使用 GitHub Actions 加密 Secrets，构建时将密钥恢复到 runner 临时目录，任务结束后删除。

固定证书 SHA-256：`E0:7A:05:09:5F:2B:19:E1:B7:B3:7D:48:E6:70:16:DA:4B:5C:F7:83:86:A0:30:20:41:0A:3A:13:4E:1F:12:AD`。

## GitHub 构建

在仓库 **Settings → Secrets and variables → Actions → New repository secret** 配置：

| 名称 | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | JKS 文件的单行 Base64 编码 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | `StudyWithMiku` |
| `ANDROID_KEY_PASSWORD` | 该别名的私钥密码 |

可在本机生成 Base64 文件；把输出文件内容粘贴到第一个 Secret，不要提交该文件：

```bash
python -c 'import base64,pathlib; pathlib.Path("StudyWithMiku-secret.b64").write_bytes(base64.b64encode(pathlib.Path("StudyWithMiku.jks").read_bytes()))'
```

然后在 **Actions → Android signed release → Run workflow**，选择 `main` 并运行。也可将 `versionName` 与 `versionCode` 更新后，推送对应 `v<versionName>` 标签自动运行。标签版本必须与 APK 中的版本一致。

工作流构建 `assembleRelease`、执行 Release 单元测试和 Lint，核对 APK 签名、固定证书、包名及 `debuggable=false`，生成：

- `StudyWithMiku-Android-<版本>-release.apk`
- `SHA256SUMS.txt`
- `SIGNING-CERTIFICATE.txt`（公开证书资料）
- `Android-release-check-reports.zip`

产物保存到私有仓库的 Release 草稿。进入对应草稿点击 **Publish release** 即可正式发布到私有仓库；这不会把仓库变公开。已发布版本不会被工作流覆盖，再发布必须提高版本。草稿与正式 Release 的访问仍受仓库权限控制。

普通 `Android app` 工作流仍构建 Debug APK；使用正式包时请选择 **Android signed release** 产物。

## 本机构建

安装 JDK 17 与 Android SDK 35 后，在 Bash 中输入密码，避免写在命令历史或代码中：

```bash
export MIKU_KEYSTORE_PATH="/absolute/path/StudyWithMiku.jks"
export MIKU_KEY_ALIAS="StudyWithMiku"
read -rsp 'Keystore password: ' MIKU_STORE_PASSWORD
printf '\n'
read -rsp 'Key password: ' MIKU_KEY_PASSWORD
printf '\n'
export MIKU_STORE_PASSWORD MIKU_KEY_PASSWORD
cd android
./gradlew --no-daemon --no-configuration-cache assembleRelease testReleaseUnitTest lintRelease
unset MIKU_STORE_PASSWORD MIKU_KEY_PASSWORD
```

输出 APK 为 `android/app/build/outputs/apk/release/app-release.apk`。缺少签名环境变量时，打包 Release 会明确失败，不会生成可误发布的未签名正式包。

可用 Android SDK 的 `apksigner verify --verbose --print-certs app-release.apk` 检查签名，再对照上面的证书指纹。固定包名为 `io.github.chorusfruit233.studywithmiku`；未来保持同密钥、同包名并提高 `versionCode`，即可覆盖安装正式版。

首次从之前的临时调试签名 APK 切换，无法直接覆盖；先保存需要的本机资料，卸载会清除本机资料。妥善保留原 JKS、别名和密码；GitHub Secrets 不提供读取原值的入口。
