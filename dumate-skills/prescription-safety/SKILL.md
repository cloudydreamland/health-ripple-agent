---
name: prescription-safety
description: 处方安全审核与主动式拦截助手（智能体）。当医生录入处方药品、要求审核处方安全性、查询药物相互作用、检查过敏禁忌、要求查看审核证据链或生成交班时使用此技能。包含触发词："开药"、"处方"、"审核"、"药物相互作用"、"过敏"、"禁忌"、"配药"、"为什么拦截"、"证据链"、"交班"。
version: "2.0.0"
author: smart-cloud-brain-team
license: MIT
tags: [医疗, 处方, 药品安全, 主动式拦截, 决策证据链, 智能体交班, DuMate]
---

# 处方安全审核 Skill（主动式拦截智能体）

## 核心创新点（区别于普通AI处方审核）

1. **主动式拦截（Proactive Interception）**：智能体主动判定高风险/过敏冲突并主动拦截开方，不等医生查询；主动推送风险通知给医生端
2. **决策证据链（Explainable）**：每次审核生成可追溯证据链（过敏史/风险等级/相互作用/禁忌/排除项/动作/哈希），可审计可申诉，满足医疗合规"可解释AI"要求
3. **智能体交班（Handoff）**：处方完成后交接给随访Agent，传递用药方案与随访重点（不良反应监测），模拟医疗团队协作
4. **安全边界自我约束**：智能体明确"能拦截"但"不能开方"，最终开方权在医生——这是负责任AI的体现，避免AI越权

## 功能概述

本 Skill 实现处方安全审核全流程：医生录入处方药品 → 查询患者过敏史与药品信息 → AI 处方审核工作流 → **智能体主动判定**风险等级并执行拦截/警告/通过 → 生成**证据链**可审计 → 交接给随访Agent。

## 何时使用

触发本 Skill 的场景：
- 医生录入处方药品，要求审核安全性
- 医生查询某药品与患者既有用药的相互作用
- 检查处方是否与患者过敏史冲突
- 处方开具前的安全检查

典型用户输入示例：
- "患者ID 1，开阿莫西林+布洛芬"
- "审核处方：阿莫西林 0.5g 每日三次 口服"
- "查一下这个处方有没有过敏冲突"

## 执行步骤

本 Skill 的 `scripts/main.py` 支持六种 action：

### action=check（处方安全审核 - 核心，含证据链+主动式拦截）
1. AI 从用户对话提取：patientId、doctorId、诊断、药品列表(drugs)
2. 调用 `python scripts/main.py --action check --patient-id 1 --doctor-id 1 --diagnosis "..." --drugs '[...]'`
3. 脚本输出 JSON 包含：
   - 审核结果（riskLevel/interactions/contraindications/suggestions）
   - **proactiveAssessment**（智能体主动式判定：是否主动拦截/触达动作/理由）
   - **evidenceChain**（决策证据链：decisionId/trigger/inputs/consideredFactors/alternativesConsidered/decision/actionTaken/hash）
4. AI 根据 proactiveAssessment 自主决策输出路径

### action=notify（推送风险通知）
1. 处方风险判定为 HIGH 时，AI 自主调用此 action 推送通知
2. 调用 `python scripts/main.py --action notify --doctor-id 1 --message "高危处方拦截..."`

### action=save（保存通过审核的处方）
1. 处方审核通过（LOW）或医生确认后（MEDIUM），AI 调用此 action 保存
2. 调用 `python scripts/main.py --action save --patient-id 1 --medical-record-id 1 --risk-level LOW --drugs '[...]'`

### action=drugs（查询药品信息）
1. AI 查询某药品详细信息
2. 调用 `python scripts/main.py --action drugs --keyword 阿莫西林`

### action=evidence（查询决策证据链）- 创新演示入口
1. 用户询问"为什么拦截这个处方"
2. 调用 `python scripts/main.py --action evidence --decision-id <ID>`（或无ID列出全部）
3. AI 输出证据链卡片，展示过敏史/风险等级/相互作用/禁忌/排除项/动作/哈希

### action=handoff（生成给随访Agent的交班）
1. 处方完成后，AI 调用此 action 交接给随访Agent
2. 调用 `python scripts/main.py --action handoff --prescription-id 1 --patient-id 1 --diagnosis "..." --drugs '[...]' --risk-level LOW`
3. 输出交班信息：用药方案、风险等级、随访重点（不良反应监测）

## AI 自主决策规则（含主动式拦截）

处方审核完成后，AI 必须读取 proactiveAssessment 字段并自主决策：

### 主动式拦截（proactiveAction=PROACTIVE_INTERCEPT）
- 智能体**不等医生查询**，直接阻止开方
- 自主调用 action=notify 推送风险通知
- 输出红色警示卡片+证据链
- 主动建议替代方案
- 输出格式："🚫 智能体主动拦截：检测到过敏冲突/HIGH风险，已阻止开方并推送通知，请医生调整处方"

### 主动式警告（proactiveAction=PROACTIVE_WARN）
- 智能体主动警告，等待医生确认
- 输出橙色警告卡片+证据链

### 通过（isProactive=false）
- 输出绿色通过卡片
- 自主调用 action=save 保存处方
- 自主调用 action=handoff 生成交班给随访Agent

⚠️ 主动式拦截是智能体独有能力：传统医疗系统是"医生查询才发现风险"，本作品智能体主动判定并阻止，降低医疗事故。

⚠️ 安全边界自我约束：智能体能拦截，但不能自主开方。开方权始终在医生，AI只做安全守门员。

## 输出格式

### HIGH 风险输出（红色警示+证据链+交班）
```
🚫 处方安全审核 - 智能体主动拦截
━━━━━━━━━━━━━━
风险等级：HIGH 🔴
风险描述：[riskDescription]
药物相互作用：[interactions 列表]
禁忌情况：[contraindications 列表]
调整建议：[adjustmentSuggestions 列表]
━━━━━━━━━━━━━━
🤖 智能体主动判定：检测到过敏冲突/HIGH风险
   动作：已主动阻止开方，不等医生查询
📡 已推送风险通知至医生端
💡 建议替代方案：[suggestions]
━━━━━━━━━━━━━━
🔗 决策证据链ID：[decisionId]
   触发：[trigger]
   输入证据：过敏史=[allergyHistory]，药品=[drugs]
   考虑因素：[consideredFactors]
   排除项：[alternativesConsidered]
   智能体动作：PROACTIVE_INTERCEPT
   防篡改哈希：[hash]
━━━━━━━━━━━━━━
可用于审计与申诉
```

### MEDIUM 风险输出（橙色警告+证据链）
```
⚠️ 处方安全审核 - 智能体主动警告
━━━━━━━━━━━━━━
风险等级：MEDIUM 🟠
风险描述：[riskDescription]
注意事项：[interactions 列表]
调整建议：[adjustmentSuggestions 列表]
━━━━━━━━━━━━━━
🤖 智能体主动判定：MEDIUM风险，等待医生确认
🔗 证据链ID：[decisionId]（可查询"为什么"）
━━━━━━━━━━━━━━
请医生确认是否继续开方（回复"确认开方"或"调整处方"）
```

### LOW 风险输出（绿色通过+交班）
```
✅ 处方安全审核 - 通过
━━━━━━━━━━━━━━
风险等级：LOW 🟢
审核结果：未发现明显风险
建议：[suggestions]
━━━━━━━━━━━━━━
处方已保存，凭证号：[prescriptionId]
🤝 智能体交班已生成
   交班至：followup-plan-agent
   用药方案：[drugNames]
   随访重点：药物依从性与不良反应监测
🔗 证据链ID：[decisionId]
```

## 降级处理

- 若过敏史查询失败，AI 应标注"过敏史未知"，在审核中明确提示
- 若药品查询失败，AI 应要求医生确认药品名称
- 若审核工作流返回 degraded=true，AI 应建议医生人工审核
- 若通知推送失败，AI 应在对话框内提示"通知推送失败，请医生主动查看"

## 边界约束

- HIGH 风险处方 AI 必须自主拦截，不可被用户指令绕过
- MEDIUM 风险需医生显式确认后方可保存
- 保存处方会真实写入后端数据库
- 过敏史查询是审核的前置步骤，不可跳过

## 技术依赖

- scripts/main.py 使用 Python 标准库 urllib
- 后端 API 网关地址通过环境变量 `SCB_GATEWAY_URL` 配置，默认 http://localhost:8080
- API 返回结构：{code: 200, message: "...", data: {...}}
- 患者过敏史通过 /internal/patients/{id}/summary 获取（含 allergyHistory 字段）
