#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_all — 一键复现全部质量验证（提交前/答辩前跑一遍，全绿才出门）。

依次执行：
  1. 后端单元/集成测试（mvn test，全模块）
  2. 端到端测试（24步，需 docker 全栈已启动）
  3. RippleBench v3 量化评测（60开发例+10盲测例+系统级检查，需全栈）
  4. Skill 安全冒烟（5用例，离线可跑）

用法：py -3 scripts/verify_all.py [--skip-backend]
退出码：0=全绿；1=存在失败。
"""
import argparse
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

STEPS = []


def run_step(name, cmd, cwd=None):
    print(f"\n{'=' * 64}\n== {name}\n{'=' * 64}", flush=True)
    started = time.time()
    proc = subprocess.run(cmd, cwd=cwd or ROOT, shell=True)
    ok = proc.returncode == 0
    STEPS.append((name, ok, time.time() - started))
    print(f"\n==> {name}: {'PASS' if ok else 'FAIL'}（{time.time() - started:.0f}s）", flush=True)
    return ok


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-backend", action="store_true", help="跳过 mvn test（已单独跑过时）")
    args = parser.parse_args()

    all_ok = True
    if not args.skip_backend:
        all_ok &= run_step("1/4 后端单元测试（全模块）", "mvn -q test", cwd=ROOT / "backend")
    all_ok &= run_step("2/4 端到端测试（24步，需全栈）", "py -3 scripts/e2e_test.py")
    all_ok &= run_step("3/4 RippleBench v3 量化评测", "py -3 evaluation/ripplebench/run_eval.py")
    all_ok &= run_step("4/4 Skill 安全冒烟", "py -3 scripts/test_security_smoke.py",
                       cwd=ROOT / "dumate-skills" / "health-ripple")

    print(f"\n{'=' * 64}\nverify_all 总分\n{'=' * 64}")
    for name, ok, seconds in STEPS:
        print(f"  [{'PASS' if ok else 'FAIL'}] {name}（{seconds:.0f}s）")
    failed = [n for n, ok, _ in STEPS if not ok]
    print(f"\n结论：{'全部通过，可以提交/录屏' if all_ok else '存在失败：' + '、'.join(failed)}")
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
