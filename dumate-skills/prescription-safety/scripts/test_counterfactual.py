#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""prescription-safety Skill 反事实决策树测试脚本"""
import sys
import os
import json

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import main as rx_main

print("=" * 70)
print("测试：处方审核反事实决策树 - 青霉素过敏+阿莫西林（核心创新高光演示）")
print("=" * 70)

# 测试反事实决策树构建函数
drugs = [{"drugName": "阿莫西林", "dosage": "0.5g", "frequency": "每日三次", "usageMethod": "口服"}]
counterfactual = rx_main._build_counterfactual_tree(
    chosen_path="AI自主拦截高风险处方并建议替代方案",
    drugs=drugs,
    allergy_history="青霉素",
    risk_level="HIGH",
    suggestions=["阿奇霉素", "克拉霉素"],
    interactions=["与布洛芬合用增加出血风险"],
)
print("反事实决策树：")
print(json.dumps(counterfactual, ensure_ascii=False, indent=2))

print("\n" + "=" * 70)
print("测试2：MEDIUM风险反事实决策树")
print("=" * 70)

counterfactual2 = rx_main._build_counterfactual_tree(
    chosen_path="AI警告风险，等待医生确认后方可开方",
    drugs=drugs,
    allergy_history="",
    risk_level="MEDIUM",
    suggestions=[],
    interactions=["轻度相互作用"],
)
print(json.dumps(counterfactual2, ensure_ascii=False, indent=2))

print("\n" + "=" * 70)
print("测试3：LOW风险反事实决策树（无反事实路径）")
print("=" * 70)

counterfactual3 = rx_main._build_counterfactual_tree(
    chosen_path="AI通过处方审核",
    drugs=drugs,
    allergy_history="",
    risk_level="LOW",
    suggestions=[],
    interactions=[],
)
print(json.dumps(counterfactual3, ensure_ascii=False, indent=2))

print("\n" + "=" * 70)
print("所有测试完成！")
print("=" * 70)
