# 安全自审报告 SECURITY-SELF-AUDIT

> 生成于 2026-09-20 00:46 · 脚本：scripts/security_self_audit.py（CI 同源规则，可复跑）
> 口径：只报高置信硬编码凭据模式；演示种子凭据与环境变量默认值单独披露（见下），不算泄漏但生产必须覆盖。

## 结果：✅ 未发现硬编码凭据泄漏

- 扫描范围：git 追踪的源码/配置/文档（跳过依赖与构建产物）
- 高危发现：0 处
- 已知披露（演示种子/环境默认/代码引用形态）：42 处
- 被追踪的 .env 文件：0 个

## 待处理发现

（无）

## 已知披露（演示种子/环境默认，生产必须覆盖轮换）

- backend\auth-service\src\main\java\com\smartcloudbrain\auth\controller\InternalAuthController.java:27: token=author***  [环境默认/代码引用]
- backend\common-lib\src\main\java\com\smartcloudbrain\common\security\InternalRequestGuard.java:24: Token=expect***  [环境默认/代码引用]
- backend\common-lib\src\main\java\com\smartcloudbrain\common\security\InternalRequestGuard.java:28: Token=reques***  [环境默认/代码引用]
- backend\common-lib\src\main\java\com\smartcloudbrain\common\security\JwtService.java:24: secret=secret***  [环境默认/代码引用]
- backend\gateway-service\src\main\java\com\smartcloudbrain\gateway\security\JwtGatewayFilter.java:50: token=resolv***  [环境默认/代码引用]
- backend\mcp-server\app\main.py:34: token=backen***  [环境默认/代码引用]
- backend\mcp-server\app\main.py:51: token=requir***  [环境默认/代码引用]
- backend\ripple-service\src\main\java\com\smartcloudbrain\ripple\security\RippleSecurityGuard.java:35: Token=expect***  [环境默认/代码引用]
- backend\ripple-service\src\main\java\com\smartcloudbrain\ripple\service\FamilyShareService.java:57: token=base64***  [环境默认/代码引用]
- backend\ripple-service\src\test\java\com\smartcloudbrain\ripple\FamilyShareRadarTest.java:56: token=String***  [环境默认/代码引用]
- backend\ripple-service\src\test\java\com\smartcloudbrain\ripple\FamilyShareRadarTest.java:64: token=String***  [环境默认/代码引用]
- deploy\docker-compose.yml:25: PASSWORD=-kingb***  [环境默认/代码引用]
- deploy\docker-compose.yml:38: PASSWORD=$$DB_P***  [环境默认/代码引用]
- deploy\docker-compose.yml:82: PASSWORD=-scb_p***  [环境默认/代码引用]
- dumate-skills\health-ripple\scripts\main.py:42: TOKEN=os.env***  [环境默认/代码引用]
- dumate-skills\health-ripple\scripts\main.py:1172: TOKEN=args.a***
- evaluation\ripplebench\run_eval.py:158: token=client***  [环境默认/代码引用]
- evaluation\ripplebench\run_eval.py:160: token=client***  [环境默认/代码引用]
- frontend\apps\patient-mobile\pages\index\index.vue:227: password=this.p***
- frontend\apps\patient-web\src\pages\FamilySharePage.vue:27: token=comput***  [环境默认/代码引用]
- frontend\apps\patient-web\src\pages\MyRipplePage.vue:175: token=auth.t***  [环境默认/代码引用]
- frontend\apps\ripple-console\src\api.ts:17: PASSWORD=localS***  [环境默认/代码引用]
- frontend\apps\ripple-console\src\api.ts:72: password=DOCTOR***
- frontend\apps\ripple-console\src\api.ts:76: token=body.d***
- frontend\packages\shared-api\src\__tests__\api.test.ts:15: token=jwt.pa***
- frontend\packages\shared-api\src\__tests__\api.test.ts:20: token=jwt.pa***
- mcp\ripple-mcp-server.mjs:27: PASSWORD=proces***
- mcp\ripple-mcp-server.mjs:36: password=DOCTOR***
- mcp\ripple-mcp-server.mjs:42: token=body.d***
- scripts\e2e_test.py:118: token=data.g***  [环境默认/代码引用]
- scripts\e2e_test.py:175: token=data.g***  [环境默认/代码引用]
- scripts\e2e_test.py:647: token=link.g***  [环境默认/代码引用]
- scripts\init-db.sh:18: password=$DB_PA***  [环境默认/代码引用]
- scripts\setup-demo.sh:185: API_KEY=$TRIAG***  [环境默认/代码引用]
- scripts\setup-demo.sh:186: API_KEY=$MEDIC***  [环境默认/代码引用]
- scripts\setup-demo.sh:187: API_KEY=$PRESC***  [环境默认/代码引用]
- scripts\setup-demo.sh:189: API_KEY=.*|DIF***  [环境默认/代码引用]
- scripts\setup-demo.sh:190: API_KEY=.*|DIF***  [环境默认/代码引用]
- scripts\setup-demo.sh:191: API_KEY=.*|DIF***  [环境默认/代码引用]
- scripts\verify-kingbase.ps1:26: PASSWORD=$Passw***  [环境默认/代码引用]
- scripts\verify-kingbase.ps1:27: PASSWORD=$Passw***  [环境默认/代码引用]
- scripts\verify-kingbase.ps1:56: PASSWORD=$($env***  [环境默认/代码引用]

## 生产部署要求（deploy/env/ 模板）

- JWT 密钥、内部服务令牌、RIPPLE_SHARE_SECRET（家属圈签名）、数据库/RabbitMQ 口令全部经环境变量注入；
- 上述「演示种子」仅用于本地演示与自动化测试，任何对外部署必须先覆盖并轮换。
