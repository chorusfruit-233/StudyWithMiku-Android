# Release 构建、签名与公开发布

仓库及已发布的 Release 均公开。当前正式版为 [v1.2.9](https://github.com/chorusfruit-233/StudyWithMiku-Android/releases/tag/v1.2.9)，可直接下载 `StudyWithMiku-Android-1.2.9-release.apk`。未发布的草稿仅对有仓库写入权限的维护者可见。

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

1. 更新 `android/app/build.gradle.kts` 中的 `versionName` 与 `versionCode`，更新 [CHANGELOG.md](../CHANGELOG.md) 和 [.github/release-notes.md](../.github/release-notes.md)。当前 `v1.2.9` 已发布，新版本必须提高版本号。
2. 提交并推送源码。在 **Actions → Android signed release → Run workflow** 选择 `main` 运行；也可推送与 `versionName` 一致的 `v<versionName>` 标签触发构建。标签版本必须与 APK 版本一致。
3. 检查构建、Release 单元测试、Lint 和 APK 签名验证结果。进入工作流生成的 Release 草稿，确认版本、说明及附件。
4. 点击 **Publish release**。发布后，Release 页面和 APK 附件可公开访问，适合作为用户下载入口。

工作流先把 JKS 恢复到 runner 临时目录，通过环境变量提供密码，执行 `assembleRelease testReleaseUnitTest lintRelease`。随后核对 APK 签名、固定证书、包名、版本和 `debuggable=false`，生成：

- `StudyWithMiku-Android-<版本>-release.apk`
- `SHA256SUMS.txt`
- `SIGNING-CERTIFICATE.txt`（公开证书资料）
- `Android-release-check-reports.zip`

草稿确认后再公开发布；工作流不会覆盖已发布的版本，任务结束后删除临时 JKS。公开仓库不改变签名 Secrets 的保管方式。

普通 **Android app** 工作流构建 Debug APK，并保留为 Debug 草稿；面向用户的正式包请选择 **Android signed release** 产物。草稿不会出现在公众的下载列表中。

## 本机构建

安装 JDK 17、Android SDK 35 与 Build Tools 35.0.0 后，在仓库根目录使用 Bash 输入密码：

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

脚本使用 `ANDROID_HOME` 下的 Build Tools 35.0.0，核对上述证书、包名、版本与关闭调试标志，输出规范命名的 APK、校验值与公开证书资料。本地上传时可在对应 Release 页面添加附件，完成说明和检查报告后发布。

## 下载核验与版本维护

Release 附件中的 `SHA256SUMS.txt` 是 APK 文件校验值，`SIGNING-CERTIFICATE.txt` 是公开证书资料。可使用 Android SDK 的 `apksigner verify --verbose --print-certs <APK>` 检查签名，并对照本页固定证书指纹。

维护者需保留 JKS、别名和密码的备份；GitHub Secrets 不提供读取原值的入口。独立 Fork 若采用自己的发行密钥，还需调整发布校验脚本中的预期证书指纹，其签名无法覆盖本项目正式版。

1.2.7 的签名构建在本地完成，Actions 负责上传与复核。实际验证范围及构建来源见 [VALIDATION.md](VALIDATION.md)。该版本尚未启用 R8 代码裁剪和资源裁剪，音视频保留原始编码。
