# 负载基准 PERF-BENCHMARK

> 测于 2026-09-19，脚本：scripts/bench_load.py（测什么/怎么测全部公开可复跑）。
> 环境：开发本机单节点（Docker Desktop on Windows，KingbaseES/Redis/RabbitMQ + 13 个 Spring Boot 3 微服务）。
> **诚实声明**：这是笔记本级开发环境的相对比较数字，不构成生产容量承诺；生产部署的容量规划应基于目标硬件重测。



环境注意：开发机实测，仅供相对比较，不构成生产容量承诺。

## 读路径（健康气象 / 72h预报）

| 端点 | 并发 | RPS | p50(ms) | p95(ms) | p99(ms) | 错误 |
|---|---|---|---|---|---|---|
| 气象 GET /health-weather/daily | 1 | 55.7 | 12 | 36 | 36 | 0 |
| 气象 GET /health-weather/daily | 10 | 337.5 | 22 | 36 | 39 | 0 |
| 气象 GET /health-weather/daily | 50 | 416.0 | 93 | 201 | 215 | 0 |
| 预报 GET /ripple/forecast | 1 | 47.0 | 30 | 30 | 30 | 0 |
| 预报 GET /ripple/forecast | 10 | 515.8 | 16 | 34 | 36 | 0 |
| 预报 GET /ripple/forecast | 50 | 712.1 | 64 | 87 | 103 | 0 |

## 审计路径（印鉴链全链校验：全表逐条SHA-256重算）

| 端点 | 并发 | RPS | p50(ms) | p95(ms) | p99(ms) | 错误 |
|---|---|---|---|---|---|---|
| 验印 GET /evidence/verify | 1 | 24.1 | 43 | 45 | 45 | 0 |
| 验印 GET /evidence/verify | 10 | 209.8 | 46 | 52 | 58 | 0 |

## 写路径（涟漪推演：知识库+反事实+护栏+证据入链，串行30次）

| 端点 | 并发 | RPS | p50(ms) | p95(ms) | p99(ms) | 错误 |
|---|---|---|---|---|---|---|
| 推演 POST /health-event/ripple | 1 | 42.9 | 24 | 36 | 48 | 0 |


