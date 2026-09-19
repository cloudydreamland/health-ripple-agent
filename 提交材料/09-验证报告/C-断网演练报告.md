# 断网演练报告 DRILL-OFFLINE

> 执行于 2026-09-20 00:50 · 脚本：scripts/drill_offline.py（可重复执行）
> 剧本：主动停掉 ripple-service（核心依赖）→ 验证故障被隔离在单服务 → 验证恢复自愈。

1. 基线：登录OK，预报接口 200（148ms）
2. 击穿：docker stop scb-ripple-service
3. 降级验证：预报接口失败=True（HTTP 0）——大屏将如实切SNAPSHOT徽章
4. 故障隔离：登录/鉴权链路存活=True（故障只在单服务，不是全局瘫）
5. 恢复：docker start scb-ripple-service，轮询等待自愈…
6. 恢复验证：预报接口自愈=True

## 结论：✅ 演练通过：故障被隔离、降级诚实、恢复自愈

> 演练对应的答辩话术：断网/依赖宕机时，大屏 SNAPSHOT 徽章如实翻转、屏幕上是什么数据从不撒谎；
> 恢复后自动回到 LIVE。微服务架构的价值不在'永不故障'，而在'故障可隔离、状态可诚实、恢复可自愈'。
