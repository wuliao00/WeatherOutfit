# 发布签名（release signing）

本仓库的 release 包由 CI 签名，**签名密钥不在仓库里**（也不该在）。

## CI 怎么签

`.github/workflows/ci.yml` 里有一条 `Detect release keystore secret`：读 `RELEASE_KEYSTORE_BASE64`，
非空才 `base64 -d` 出 `release.jks` 并把它交给 `assembleRelease`。四个 secret 都配齐才会出**正式签名包**；
没配时 `app/build.gradle.kts` 不建 signingConfig，产物是 `app-release-unsigned.apk`（装不上手机，
只用来验证 R8 与资源收缩没裁坏功能——CI 不因此变红）。

| Secret 名 | 值 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | release keystore（PKCS12）的 base64，**一整行、不带换行** |
| `RELEASE_KEYSTORE_PASSWORD` | keystore 口令 |
| `RELEASE_KEY_ALIAS` | key 别名 |
| `RELEASE_KEY_PASSWORD` | key 口令（PKCS12 下与 keystore 口令相同） |

⚠️ `RELEASE_KEY_PASSWORD` **不能留空**：`app/build.gradle.kts` 里写的是
`keyPassword = env ?: storePassword`，而**空串不算 null**，会走到 PKCS12 解密失败
（报 `Get Key failed: Given final block not properly padded`）。

## 本机出签名包

密钥不在仓库里，本机放在（路径仅供本机参考）：

```
E:/dev/wo-topbar-parent/keystore/     jianyi-release.jks · PASSWORD.txt · keystore.base64 · README.md
```

```bash
export RELEASE_KEYSTORE_PATH="<keystore 的绝对路径>"
export RELEASE_KEY_ALIAS=<别名>
export RELEASE_KEYSTORE_PASSWORD="$(cat <口令文件>)"
export RELEASE_KEY_PASSWORD="$RELEASE_KEYSTORE_PASSWORD"   # 必须与上面同值
./gradlew :app:assembleRelease
```

## 核对某个包是不是这把钥匙签的

```bash
apksigner verify --print-certs app-release.apk | grep "SHA-256"
```

当前正式密钥的证书指纹（**公钥信息，公开可对**）：

```
SHA-256  8161CA5493D1A7BECDCC904FD76BF5BE57519D076A37C39E7BEC1FCC7F355620
DN       CN=wuliao00, OU=WeatherOutfit, O=wuliao00, C=CN
```

## 红线

- **密钥丢了 = 已发布的包永远无法被升级覆盖**：Android 只允许同签名覆盖安装，换签名必须卸载重装、
  用户本地数据清空。密钥本体与口令请离线备份至少两处。
- 密钥文件与口令**绝不入库**（`.gitignore` 之外靠纪律）；issue / PR / 聊天里也不要贴。
- 历史包袱：2.2.0 之前对外发的都是 **debug 签名**包（各自一把 debug 钥匙），
  装过它们的设备升到正式签名版需要先卸载一次；`v2.3.0` 起改为正式签名，此后版本之间可直接覆盖升级。
