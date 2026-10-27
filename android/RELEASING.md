# Release 构建、签名与公开发布

仓库及已发布的 Release 均公开。当前正式版为 [v1.2.14](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/v1.2.14)，可直接下载 `StudyWithMiku-Android-1.2.14-release.apk`。未发布的草稿仅对有仓库写入权限的维护者可见。

## 正式签名

本项目正式版使用固定的 `StudyWithMiku.jks`，别名为 `StudyWithMiku`。签名文件和密码由维护者保管，不包含在公开源码中；贡献代码和构建 Debug 无需这些凭据。

固定证书 SHA-256：

```text
E0:7A:05:09:5F:2B:19:E1:B7:B3:7D:48:E6:70:16:DA:4B:5C:F7:83:86:A0:30:20:41:0A:3A:13:4E:1F:12:AD
```

正式包固定使用 `io.github.chorusfruit233.studywithmiku`，关闭调试。以后保持同包名、同证书并提高 `versionCode`，即可覆盖升级。此前使用临时 Debug 签名的 APK 无法直接覆盖；切换前保存需要的资料，卸载会清除本机资料。

## GitHub Actions 签名构建

在 **Settings → Secrets and variables → Actions → New repository secret** 配置以下四项 **Repository secrets**。当前工作流没有指定 Environment，因此使用仓库级密钥：

| 名称 | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | JKS 文件的单行 Base64 编码 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | `StudyWithMiku` |
| `ANDROID_KEY_PASSWORD` | 该别名的私钥密码 |

GitHub 不会显示已保存的 Secret 值，点击编辑后输入框为空是正常现象；这不表示内容丢失。是否被工作流读取，以构建日志为准。重新填写时保存完整值；本项目 JKS 的 Base64 开头为 `/u3+7Q`，复制时保留开头的 `/` 和末尾填充符。

在本机生成 Base64 文件，把内容粘贴到第一个 Secret；Base64 只是编码，生成的文件同样不要提交到 Git：

```bash
python -c 'import base64,pathlib; pathlib.Path("StudyWithMiku-secret.b64").write_bytes(base64.b64encode(pathlib.Path("StudyWithMiku.jks").read_bytes()))'
```

发布新版本时：

1. 更新 `android/app/build.gradle.kts` 中的 `versionName` 与 `versionCode`，更新 [CHANGELOG.md](../CHANGELOG.md) 和 [.github/release-notes.md](../.github/release-notes.md)。当前 `v1.2.14` 已发布，新版本必须提高版本号。
2. 提交并推送源码。在 **Actions → Android signed release → Run workflow** 选择 `main` 运行；`Android app` 同时验证该提交的 Debug 构建和设备回归。
3. 等待同一源码提交的两项工作流成功。核对 Release 草稿中的说明与附件，重新下载 APK 验证签名、版本与校验值。
4. 在 **Actions → Publish verified release → Run workflow** 填写版本标签、完整 40 位应用源码提交，以及成功的 Debug 与签名 Release 运行 ID。工作流验证版本、构建来源和附件后，直接公开发布正式版并设为 Latest。

发布工作流使用仓库自带的 `GITHUB_TOKEN` 创建标签，避免标签再次触发签名构建。文档可在构建后补充，但 `android/app` 与 Gradle 构建输入必须保持一致；应用有任何改动都需重新通过两项检查。也可在 Release 页面手动点击 **Publish release**。

工作流先把 JKS 恢复到 runner 临时目录，通过环境变量提供密码，执行 `assembleRelease testReleaseUnitTest lintRelease`。随后核对 APK 签名、固定证书、包名、版本和 `debuggable=false`，生成：

- `StudyWithMiku-Android-<版本>-release.apk`
- `SHA256SUMS.txt`
- `SIGNING-CERTIFICATE.txt`（公开证书资料）
- `Android-release-check-reports.zip`

签名构建先保存草稿，核对后通过上述发布步骤公开；构建工作流不会覆盖已发布的版本，任务结束后删除临时 JKS。公开仓库不改变签名 Secrets 的保管方式。

普通 **Android app** 工作流构建 Debug APK，并保留为 Debug 草稿；面向用户的正式包请选择 **Android signed release** 产物。草稿不会出现在公众的下载列表中。

## 本机构建

安装 JDK 17、Android SDK 37.0 与 Build Tools 37.0.0 后，在仓库根目录使用 Bash 输入密码：

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

输出为 `android/app/build/outputs/apk/release/app-release.apk`。缺少签名环境变量时，Release 打包会明确失败。

回到仓库根目录，验证并准备上传文件：

```bash
python .github/scripts/prepare-release.py android/app/build/outputs/apk/release/app-release.apk artifacts/release
```

脚本使用 `ANDROID_HOME` 下的 Build Tools 37.0.0，核对上述证书、包名、版本与关闭调试标志，输出规范命名的 APK、校验值与公开证书资料。本地上传时可在对应 Release 页面添加附件，完成说明和检查报告后发布。

## 下载核验与版本维护

Release 附件中的 `SHA256SUMS.txt` 是 APK 文件校验值，`SIGNING-CERTIFICATE.txt` 是公开证书资料。可使用 Android SDK 的 `apksigner verify --verbose --print-certs <APK>` 检查签名，并对照本页固定证书指纹。

维护者需保留 JKS、别名和密码的备份；GitHub Secrets 不提供读取原值的入口。独立 Fork 若采用自己的发行密钥，还需调整发布校验脚本中的预期证书指纹，其签名无法覆盖本项目正式版。

当前正式版在 Actions 中签名构建，实际验证范围及历史构建来源见 [VALIDATION.md](VALIDATION.md)。当前尚未启用 R8 代码裁剪和资源裁剪，音视频保留原始编码。
