#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
断网演练（Offline Drill）：主动击穿依赖，验证系统的诚实降级与恢复能力。

剧本：
  1. 基线：医生登录 + 72h预报接口正常；
  2. 击穿：docker stop scb-ripple-service（涟漪服务下线）；
  3. 验证降级：预报接口失败（网关无法转发）——大屏此时会自动切SNAPSHOT徽章；
     而登录/鉴权链路仍然存活（故障被隔离在单服务，不是全局瘫）；
  4. 恢复：docker start scb-ripple-service，轮询到接口自愈；
  5. 出报告：docs/DRILL-OFFLINE.md。

演示价值：竞品演示"一切正常"，我们敢现场拔网线——LIVE/SNAPSHOT 徽章如实翻转，
屏幕上是什么数据从不撒谎。这不是口号，是一条可重复执行的演练剧本。
"""
import datetime
import json
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE = "http://127.0.0.1:18080"
SERVICE = "scb-ripple-service"


def http(method, path, payload=None, token=None, timeout=8):
    import json
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode()
            return {"ok": True, "status": resp.status, "ms": (time.perf_counter() - started) * 1000, "body": body}
    except urllib.error.HTTPError as e:
        return {"ok": False, "status": e.code, "ms": (time.perf_counter() - started) * 1000, "body": ""}
    except Exception as e:
        return {"ok": False, "status": 0, "ms": (time.perf_counter() - started) * 1000, "body": str(e)[:120]}


def docker(*args):
    return subprocess.run(["docker", *args], capture_output=True, text=True)


def main():
    lines = [
        "# 断网演练报告 DRILL-OFFLINE",
        "",
        f"> 执行于 {datetime.datetime.now().strftime('%Y-%m-%d %H:%M')} · 脚本：scripts/drill_offline.py（可重复执行）",
        "> 剧本：主动停掉 ripple-service（核心依赖）→ 验证故障被隔离在单服务 → 验证恢复自愈。",
        "",
    ]
    ok_all = True

    # 基线
    login = http("POST", "/api/doctor/login", {"account": "doctor1", "password": "123456"})
    assert login["ok"], "基线登录失败，演练环境不就绪"
    token = json.loads(login["body"])["data"]["token"]
    base = http("GET", "/api/health-event/ripple/forecast?patientId=1", token=token)
    lines.append(f"1. 基线：登录OK，预报接口 {base['status']}（{base['ms']:.0f}ms）")
    ok_all &= base["ok"]

    # 击穿
    lines.append("2. 击穿：docker stop scb-ripple-service")
    docker("stop", SERVICE)
    time.sleep(2)
    during = http("GET", "/api/health-event/ripple/forecast?patientId=1", token=token)
    auth_during = http("POST", "/api/doctor/login", {"account": "doctor1", "password": "123456"})
    degraded = not during["ok"]
    isolated = auth_during["ok"]
    lines.append(f"3. 降级验证：预报接口失败={degraded}（HTTP {during['status']}）——大屏将如实切SNAPSHOT徽章")
    lines.append(f"4. 故障隔离：登录/鉴权链路存活={isolated}（故障只在单服务，不是全局瘫）")
    ok_all &= degraded and isolated

    # 恢复
    docker("start", SERVICE)
    lines.append("5. 恢复：docker start scb-ripple-service，轮询等待自愈…")
    recovered = False
    for _ in range(45):
        time.sleep(2)
        probe = http("GET", "/api/health-event/ripple/forecast?patientId=1", token=token)
        if probe["ok"]:
            recovered = True
            break
    lines.append(f"6. 恢复验证：预报接口自愈={recovered}")
    ok_all &= recovered

    verdict = "✅ 演练通过：故障被隔离、降级诚实、恢复自愈" if ok_all else "⚠ 演练未通过（见上）"
    lines += ["", f"## 结论：{verdict}", "",
              "> 演练对应的答辩话术：断网/依赖宕机时，大屏 SNAPSHOT 徽章如实翻转、屏幕上是什么数据从不撒谎；",
              "> 恢复后自动回到 LIVE。微服务架构的价值不在'永不故障'，而在'故障可隔离、状态可诚实、恢复可自愈'。"]
    text = "\n".join(lines) + "\n"
    print(text)
    out = ROOT / "docs" / "DRILL-OFFLINE.md"
    out.write_text(text, encoding="utf-8")
    print(f"written -> {out}")
    return 0 if ok_all else 1


if __name__ == "__main__":
    raise SystemExit(main())
