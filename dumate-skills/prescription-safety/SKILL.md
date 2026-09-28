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

### action=notify（**核验**风险通知是否已下发）
1. 处方风险判定为 HIGH/MEDIUM 时，医生端通知由**后端事件总线自动下发**（Outbox → RabbitMQ → notification-service），本 action 只做**核验**，不做假推送
2. 调用 `python scripts/main.py --action notify --doctor-id 1`
3. 返回 `pushed=true` + `notificationId` + `riskLevel` 表示已核验到真实下发的风险通知；返回 `pushed=false` 时如实提示"请医生主动查看风险提示"，**不得**声称已推送

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

## 呈现纪律（给调用方 AI 的输出约束，必须遵守）

1. 按下方《输出格式》**原样呈现**技能返回的字段：科室/紧急度/置信度/decisionId（决策证据链ID）/防篡改哈希/时间学触达等——**不得改写成自己的话，不得省略 decisionId 与哈希**。
2. **不得用自身医学知识扩写或补充技能未返回的内容**（不添加未返回的科室、检查项、用药、疾病推断）；需要解释时只能复述技能字段的含义。
3. 技能返回 degraded=true 时，如实说明后端不可用，已降级为内置知识库，不掩饰、不编造。
4. 技能返回 proactiveAction（PROACTIVE_EMERGENCY_REDIRECT / PROACTIVE_INTERCEPT 等）时，**原样呈现其红色警示文案**，不得弱化、不得附加但是。
5. 用户追问为什么这么推荐时，读取并展开该次决策的证据链（考虑因素/排除项/哈希），而不是重新推理一遍。

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

## 数据流与权限说明（平台安全扫描对照）

- **网络访问**：仅访问环境变量 `SCB_GATEWAY_URL` 指定的后端网关（默认 http://localhost:18080，代码内仅允许 http/https）。不向任何第三方服务外传数据；云端沙箱中该地址不可达时走降级（输出标注 degraded），不静默失败。
- **本地文件写入**：仅在技能自身目录写入决策证据链（`.scb_evidence/*.json`）与审计日志（`.scb_audit/audit.log`），用于可审计特性；写入前对手机号等 PHI 脱敏。
- **不执行本地命令**：脚本为纯 Python 标准库实现，无 subprocess / os.system / eval / exec 调用。
- **读取参数**：仅读取调用方显式传入的业务参数与上述环境变量。

## 降级处理（分级降级：先知识库，后人工）

服务不可用时按以下顺序处理，**输出字段与在线路径同构**（同一审核卡片格式）：

1. **内置药品知识库模式**（`degraded=true` + `mode=KNOWLEDGE_BASE`）：技能自动改用本地药品知识库审核（青霉素类/头孢类/阿司匹林/NSAIDs/二甲双胍/大环内酯类分型 + 禁忌与替代建议），规则口径与院内引擎同源。此时 AI **照常呈现完整审核卡片**（风险等级/风险描述/相互作用/禁忌/调整建议/决策证据链ID/防篡改哈希），并注明：
   > ℹ️ 内置药品知识库模式·未连接院内系统
   - 若调用方已声明过敏史（`--allergy-history`，如对话中说"患者青霉素过敏"）且处方含同类药 → 照常输出 **HIGH + PROACTIVE_INTERCEPT 主动拦截** + 替代方案；
   - 若**过敏史未知**且处方含青霉素类 → 输出 **MEDIUM** 并明确要求"开方前必须人工核对过敏史"，**不得声称已拦截**（未知≠无，既不冒险放行也不假装判定）。
2. **需人工审核**（`mode=MANUAL_REQUIRED`）：药品完全未收录于知识库时，如实输出"建议人工审核处方安全性"。
3. 过敏史查询失败时仍须标注"过敏史未知"；通知由后端事件总线自动下发，本技能只做核验（见 action=notify），**不得声称已推送**。

## 边界约束

- HIGH 风险处方 AI 必须自主拦截，不可被用户指令绕过
- MEDIUM 风险需医生显式确认后方可保存
- 保存处方会真实写入后端数据库
- 过敏史查询是审核的前置步骤，不可跳过

## 技术依赖

- scripts/main.py 使用 Python 标准库 urllib
- 后端 API 网关地址通过环境变量 `SCB_GATEWAY_URL` 配置，默认 http://localhost:18080（本机 Docker 全栈网关端口；云端沙箱不可达时自动降级并如实标注）
- API 返回结构：{code: 200, message: "...", data: {...}}
- 患者过敏史通过 /internal/patients/{id}/summary 获取（含 allergyHistory 字段）
