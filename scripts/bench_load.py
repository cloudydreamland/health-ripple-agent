#!/usr/bin/env python3
"""守护平台负载基准（诚宽数字版）。

测什么：
- /api/health-weather/daily      健康气象（中等：聚合触达+RII强度）
- /api/health-event/ripple/forecast 72小时预报（重：逐触达高斯叠加+消解联动）
- /api/evidence/verify           印鉴链全链校验（重：全表逐条SHA-256重算）
- /api/health-event/ripple       涟漪推演（最重：知识库推演+反事实+护栏+证据入链）

怎么测：固定并发 × 固定请求数，统计 p50/p95/p99/RPS/错误数。数字只反映
"本机单节点 Docker Desktop + Kingbase" 环境，不外推到生产集群——报告里写清楚。
"""
import json
import statistics
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE = "http://127.0.0.1:18080"
PATIENT_ID = 1


def login(account, password):
    req = urllib.request.Request(BASE + "/api/doctor/login", data=json.dumps(
        {"account": account, "password": password}).encode(), method="POST",
        headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read())["data"]["token"]


def call(token, path, payload=None):
    data = json.dumps(payload, ensure_ascii=False).encode() if payload is not None else None
    req = urllib.request.Request(BASE + path, data=data, method="POST" if payload is not None else "GET")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    started = time.perf_counter()
    with urllib.request.urlopen(req, timeout=60) as resp:
        body = json.loads(resp.read().decode())
    elapsed = (time.perf_counter() - started) * 1000
    if body.get("code") not in (0, 200):
        raise RuntimeError(f"{path} -> {body}")
    return elapsed


def bench(name, token, path, payload, concurrency, per_worker):
    latencies, errors = [], 0

    def worker(_):
        nonlocal errors
        try:
            latencies.append(call(token, path, payload))
        except Exception:
            errors += 1

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(worker, i) for i in range(concurrency * per_worker)]
        for f in as_completed(futures):
            f.result()
    wall = time.perf_counter() - started
    latencies.sort()
    n = len(latencies)
    if n == 0:
        print(f"| {name} | {concurrency} | 0 | - | - | - | {errors} |")
        return
    p = lambda q: latencies[min(n - 1, int(n * q))]
    print(f"| {name} | {concurrency} | {n / wall:.1f} | {p(0.5):.0f} | {p(0.95):.0f} | {p(0.99):.0f} | {errors} |")


def main():
    token = login("doctor1", "123456")
    print("# 负载基准（本机单节点：Docker Desktop + Kingbase + 13微服务）\n")
    print("环境注意：开发机实测，仅供相对比较，不构成生产容量承诺。\n")

    print("## 读路径（健康气象 / 72h预报）\n")
    print("| 端点 | 并发 | RPS | p50(ms) | p95(ms) | p99(ms) | 错误 |")
    print("|---|---|---|---|---|---|---|")
    for c in (1, 10, 50):
        bench(f"气象 GET /health-weather/daily", token, f"/api/health-weather/daily?patientId={PATIENT_ID}", None, c, 4)
    for c in (1, 10, 50):
        bench(f"预报 GET /ripple/forecast", token, f"/api/health-event/ripple/forecast?patientId={PATIENT_ID}", None, c, 4)

    print("\n## 审计路径（印鉴链全链校验：全表逐条SHA-256重算）\n")
    print("| 端点 | 并发 | RPS | p50(ms) | p95(ms) | p99(ms) | 错误 |")
    print("|---|---|---|---|---|---|---|")
    for c in (1, 10):
        bench(f"验印 GET /evidence/verify", token, "/api/evidence/verify", None, c, 3)

    print("\n## 写路径（涟漪推演：知识库+反事实+护栏+证据入链，串行30次）\n")
    print("| 端点 | 并发 | RPS | p50(ms) | p95(ms) | p99(ms) | 错误 |")
    print("|---|---|---|---|---|---|---|")
    bench("推演 POST /health-event/ripple", token, "/api/health-event/ripple",
          {"diagnosis": "2型糖尿病", "drugs": [{"drugName": "二甲双胍"}], "patientId": PATIENT_ID,
           "pastHistory": "高血压"}, 1, 30)
    print("\n_done")


if __name__ == "__main__":
    main()
