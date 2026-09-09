# -*- coding: utf-8 -*-
"""临时验证脚本：Skill 安全增强功能冒烟测试（subprocess 列表参数避开 shell 转义）。"""
import json
import subprocess
import sys
import os

BASE = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.join(BASE, "main.py")


def run(args):
    proc = subprocess.run([sys.executable, MAIN] + args, capture_output=True, text=True,
                          encoding="utf-8", cwd=BASE, timeout=60)
    return json.loads(proc.stdout)


# 用例1：正常涟漪推演（后端不可达 → 降级模式），验证脱敏+审计+安全边界
r1 = run(["--action", "ripple", "--diagnosis", "2型糖尿病",
          "--drugs", json.dumps([{"drugName": "二甲双胍"}], ensure_ascii=False),
          "--patient-id", "1", "--past-history", "高血压,联系电话13812345678"])
assert "rippleGraph" in r1, f"用例1失败: {r1}"
assert r1.get("safetyBoundary", {}).get("canPrescribe") is False, "缺安全边界声明"
assert r1["rippleGraph"].get("degraded") is True, "应处于降级模式"
print("用例1 通过: 降级涟漪推演 + safetyBoundary + 高风险MDT建议 =", r1["proactiveAssessment"]["recommendMdt"])

# 用例2：输入校验（超长诊断）
r2 = run(["--action", "ripple", "--diagnosis", "超长" * 100, "--patient-id", "1"])
assert r2.get("error") == "invalid_input", f"用例2失败: {r2}"
print("用例2 通过: 超长输入被拒绝 →", r2["message"])

# 用例3：审计日志已脱敏且为防篡改链式结构
audit_path = os.path.join(BASE, "..", ".scb_audit", "audit.log")
audit_path = os.path.abspath(audit_path)
with open(audit_path, "r", encoding="utf-8") as f:
    lines = [json.loads(ln) for ln in f.read().splitlines() if ln.strip()]
assert len(lines) >= 2, "审计日志应至少2条"
first = lines[0]
assert first.get("prevHash") == "GENESIS", "首条审计 prevHash 应为 GENESIS"
ph_raw = json.dumps(lines[0]["params"], ensure_ascii=False)
assert "13812345678" not in ph_raw, "审计日志泄露手机号明文!"
assert "138****78" in ph_raw, "手机号应已脱敏"
# 链式校验：每行 prevHash == 前一行 entryHash
import hashlib
for i in range(1, len(lines)):
    assert lines[i]["prevHash"] == lines[i - 1]["entryHash"], f"审计链在第{i}条断裂"
print(f"用例3 通过: 审计日志{len(lines)}条，链式哈希完整，PHI已脱敏")

# 用例4：证据落盘脱敏（降级模式下本地证据的 inputs.pastHistory）
ev_dir = os.path.abspath(os.path.join(BASE, "..", ".scb_evidence"))
import glob
latest = max(glob.glob(os.path.join(ev_dir, "RIPPLE_*.json")), key=os.path.getmtime)
with open(latest, "r", encoding="utf-8") as f:
    ev = json.load(f)
raw = json.dumps(ev, ensure_ascii=False)
assert "13812345678" not in raw, "证据文件泄露手机号明文!"
assert "138****78" in raw, "证据文件手机号应已脱敏"
print("用例4 通过: 本地证据落盘已脱敏 →", os.path.basename(latest))

# 用例5：MDT 动作带安全边界
r5 = run(["--action", "mdt", "--patient-id", "1", "--chief-complaint", "胸闷气短3天",
          "--past-history", "糖尿病,高血压,慢性肾病"])
assert "consultation" in r5 and len(r5["consultation"]) == 5, f"用例5失败: {list(r5.keys())}"
assert r5.get("safetyBoundary", {}).get("advisoryOnly") is True
print("用例5 通过: 五Agent MDT会诊 + safetyBoundary")

print("\n全部5个安全用例通过")
