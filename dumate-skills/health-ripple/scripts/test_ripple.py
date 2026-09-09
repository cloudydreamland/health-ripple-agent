#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""health-ripple Skill 独立运行测试脚本（核心创新高光演示）"""
import sys
import os
import json

# 添加 scripts 目录到路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# 导入 main 模块的函数
import main as ripple_main

print("=" * 70)
print("测试1：涟漪推演（核心创新高光演示）- 2型糖尿病+二甲双胍")
print("=" * 70)

# 模拟 args 对象
class Args:
    action = "ripple"
    gateway_url = None
    patient_id = "1"
    diagnosis = "2型糖尿病"
    drugs = '[{"drugName":"二甲双胍"}]'
    chief_complaint = None
    past_history = "糖尿病,高血压"
    drug = None
    decision_id = None

args = Args()
result = ripple_main.action_ripple(args)
print(json.dumps(result, ensure_ascii=False, indent=2))

print("\n" + "=" * 70)
print("测试2：MDT会诊 - 65岁多病共存患者")
print("=" * 70)

class MdtArgs:
    action = "mdt"
    gateway_url = None
    patient_id = "1"
    diagnosis = "冠心病"
    drugs = '[{"drugName":"阿司匹林"}]'
    chief_complaint = "胸闷气短3天"
    past_history = "糖尿病,高血压,慢性肾病"
    drug = None
    decision_id = None

mdt_args = MdtArgs()
mdt_result = ripple_main.action_mdt(mdt_args)
print(json.dumps(mdt_result, ensure_ascii=False, indent=2))

print("\n" + "=" * 70)
print("测试3：药物-生活冲突查询 - 华法林")
print("=" * 70)

class ConflictArgs:
    action = "conflict"
    gateway_url = None
    patient_id = None
    diagnosis = None
    drugs = None
    chief_complaint = None
    past_history = None
    drug = "华法林"
    decision_id = None

conflict_args = ConflictArgs()
conflict_result = ripple_main.action_conflict(conflict_args)
print(json.dumps(conflict_result, ensure_ascii=False, indent=2))

print("\n" + "=" * 70)
print("测试4：并发症信号查询 - 2型糖尿病")
print("=" * 70)

class CompArgs:
    action = "complication"
    gateway_url = None
    patient_id = None
    diagnosis = "2型糖尿病"
    drugs = None
    chief_complaint = None
    past_history = None
    drug = None
    decision_id = None

comp_args = CompArgs()
comp_result = ripple_main.action_complication(comp_args)
print(json.dumps(comp_result, ensure_ascii=False, indent=2))

print("\n" + "=" * 70)
print("测试5：反事实决策树查询")
print("=" * 70)

class EvArgs:
    action = "evidence"
    gateway_url = None
    patient_id = None
    diagnosis = None
    drugs = None
    chief_complaint = None
    past_history = None
    drug = None
    decision_id = None

ev_args = EvArgs()
ev_result = ripple_main.action_evidence(ev_args)
print(json.dumps(ev_result, ensure_ascii=False, indent=2))

print("\n" + "=" * 70)
print("所有测试完成！")
print("=" * 70)
