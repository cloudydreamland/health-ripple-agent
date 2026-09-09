#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""medical-triage Skill 反事实决策树测试脚本"""
import sys
import os
import json

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import main as triage_main

print("=" * 70)
print("测试1：分诊反事实决策树 - 发烧咳嗽（常规分诊）")
print("=" * 70)

counterfactual = triage_main._build_counterfactual_tree(
    chosen_path="AI分诊至呼吸内科，紧急度ROUTINE",
    recommended_dept="呼吸内科",
    urgency_level="ROUTINE",
    confidence=0.92,
    chief_complaint="发烧咳嗽3天",
)
print(json.dumps(counterfactual, ensure_ascii=False, indent=2))

print("\n" + "=" * 70)
print("测试2：分诊反事实决策树 - 胸痛（急诊）")
print("=" * 70)

counterfactual2 = triage_main._build_counterfactual_tree(
    chosen_path="AI主动引导急诊通道（心内科预备）",
    recommended_dept="心内科",
    urgency_level="EMERGENCY",
    confidence=0.95,
    chief_complaint="持续胸痛15分钟",
)
print(json.dumps(counterfactual2, ensure_ascii=False, indent=2))

print("\n" + "=" * 70)
print("测试3：分诊反事实决策树 - 低置信度（主动建议人工复核）")
print("=" * 70)

counterfactual3 = triage_main._build_counterfactual_tree(
    chosen_path="AI建议人工导诊复核（置信度低）",
    recommended_dept="消化内科",
    urgency_level="ROUTINE",
    confidence=0.45,
    chief_complaint="腹痛",
)
print(json.dumps(counterfactual3, ensure_ascii=False, indent=2))

print("\n" + "=" * 70)
print("所有测试完成！")
print("=" * 70)
