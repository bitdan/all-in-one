# TOTP 算法组件

`TotpNative` 是项目内的 RFC 6238 算法实现，目前支持 SHA1、SHA256、SHA512 以及 6/8 位验证码。

原 `/api/totp/*` 本站二次登录控制器已经移除。Tool Hub 的第三方 2FA 账号管理由
`com.linger.module.toolhub.twofactor` 提供，公开接口统一位于 `/api/v1/2fa/accounts`。

2FA 管理器继续使用 Redis Key `totp:accounts:{userId}`。Java 服务首次读取 Python 遗留的明文 JSON 时，
会使用 `TOOL_HUB_TOTP_MASTER_KEY` 原位升级为 AES-GCM 密文；这个主密钥至少 16 个字符，部署后必须保持稳定。

`TotpService` 和相关模型暂时保留给已有 Java 内部调用与测试使用，但不再暴露 HTTP 接口。

验证核心算法：

```powershell
mvn -pl module "-Dtest=TotpNativeRfcTest,TwoFactorCryptoTest" test
```
