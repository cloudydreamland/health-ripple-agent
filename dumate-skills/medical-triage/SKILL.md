---
name: medical-triage
description: 智能医疗分诊导诊助手（主动式智能体）。当用户描述症状、要求分诊、询问应该挂哪个科室、需要推荐医生号源、想要预约挂号、要求查询决策证据或审计时使用此技能。包含触发词："分诊"、"导诊"、"挂哪个科"、"症状"、"预约挂号"、"哪里不舒服"、"为什么这么推荐"、"证据链"、"交班"。
version: "2.0.0"
author: smart-cloud-brain-team
license: MIT
tags: [医疗, 导诊, 分诊, 挂号, 主动式智能体, 决策证据链, DuMate]
---

# 智能医疗分诊导诊 Skill（主动式智能体）

## 核心创新点（区别于普通AI分诊）

1. **主动式触达（Proactive）**：智能体主动判定急诊指征并引导，不等用户选择号源；置信度低于阈值主动建议人工复核，不强行AI决策
2. **决策证据链（Explainable）**：每个分诊决策生成可追溯证据链（触发条件/考虑因素/排除项/置信度/动作/哈希），可审计可申诉，满足医疗合规
3. **智能体交班（Handoff）**：分诊完成后生成交班信息给接诊Agent，传递紧急度/注意事项/建议方法，模拟医疗团队协作
4. **多角色协作**：本Agent只负责分诊，明确边界，将病历/处方/随访交给其他专精Agent

## 功能概述

本 Skill 实现基层医疗的智能分诊导诊全流程：患者用自然语言描述症状 → AI 分诊推荐科室 → **智能体主动判定**紧急度并引导 → 查询可约号源 → 完成预约挂号 → **生成交班**给接诊Agent。每个决策生成**证据链**可审计。

## 何时使用

触发本 Skill 的场景：
- 用户描述身体症状，询问应该看哪个科室
- 用户要求查询某科室的医生排班与号源
- 用户要求预约挂号
- 护士/导诊台工作人员进行分诊
- 用户要求查看"为什么这样分诊"的决策证据
- 要求审计某次分诊决策

典型用户输入示例：
- "患者男，35岁，发烧咳嗽3天，略有胸闷"
- "胸痛伴随呼吸困难"（智能体将主动判定急诊）
- "我想知道呼吸内科明天有哪些医生出诊"
- "约王医生明天上午的号"
- "刚才那次分诊为什么推荐呼吸内科？给我证据链"

## 执行步骤

本 Skill 的 `scripts/main.py` 支持五种 action：

### action=triage（分诊，含证据链+主动式判定）
1. AI 从用户对话提取：主诉、症状、年龄、性别、过敏史、既往史、患者ID(可选)
2. 调用 `python scripts/main.py --action triage --chief-complaint "..." --symptoms "..." --age 35 --gender 男`
3. 脚本输出 JSON 包含：
   - 分诊结果（recommendedDepartment/urgencyLevel/confidence/reason）
   - **proactiveAssessment**（智能体主动式判定结果：是否主动触达/触达动作/理由）
   - **evidenceChain**（决策证据链：decisionId/trigger/inputs/consideredFactors/alternativesConsidered/decision/confidence/actionTaken/hash）
4. AI 根据 proactiveAssessment 自主决策输出路径

### action=schedule（查号源）
1. AI 从分诊结果获取 departmentCode，或用户直接指定科室
2. 调用 `python scripts/main.py --action schedule`
3. 读取脚本输出 JSON，AI 自主筛选目标科室的医生与号源

### action=register（预约挂号，含智能体交班）
1. AI 从用户对话获取：doctorId、departmentId、appointmentTime
2. 调用 `python scripts/main.py --action register --doctor-id 1 --department-id 2 --appointment-time "2026-09-10T09:00:00"`
3. 脚本输出 JSON 包含：
   - 挂号结果
   - **handoff**（给接诊Agent的交班信息：fromAgent/toAgent/patientContext/triageSummary/attentions/suggestedApproach）
4. AI 输出挂号成功，并提示已生成交班

### action=evidence（查询决策证据链）- 创新演示入口
1. 用户询问某次决策的"为什么"
2. 调用 `python scripts/main.py --action evidence --decision-id <ID>`（或无ID列出全部）
3. AI 输出证据链卡片，展示触发条件/考虑因素/排除项/动作/哈希

### action=handoff（显式生成交班）
1. 调用 `python scripts/main.py --action handoff --registration-id 1`
2. 输出交班信息

## AI 自主决策规则（含主动式触达）

分诊完成后，AI 必须读取 proactiveAssessment 字段并自主决策：

### 主动式急诊引导（proactiveAction=PROACTIVE_EMERGENCY_REDIRECT）
- 智能体**不等用户选择号源**，直接输出红色警示引导急诊
- 输出格式："⚠️ 智能体主动判定：您描述的症状含急诊指征（胸痛/呼吸困难），已主动引导急诊通道，不再走普通挂号流程"
- 这是"主动式"核心创新：传统系统等用户选号源才发现该挂急诊，我们智能体主动判定

### 主动建议人工复核（proactiveAction=PROACTIVE_REQUEST_HUMAN_REVIEW）
- AI置信度<0.6时，智能体**主动**建议人工复核，不强行AI决策
- 输出格式："🤔 智能体主动建议：AI置信度0.52偏低，已主动请求人工导诊复核，避免误判"

### 常规推荐（isProactive=false）
- 正常推荐号源，AI 输出科室+号源卡片

## 呈现纪律（给调用方 AI 的输出约束，必须遵守）

1. 按下方《输出格式》**原样呈现**技能返回的字段：科室/紧急度/置信度/decisionId（决策证据链ID）/防篡改哈希/时间学触达等——**不得改写成自己的话，不得省略 decisionId 与哈希**。
2. **不得用自身医学知识扩写或补充技能未返回的内容**（不添加未返回的科室、检查项、用药、疾病推断）；需要解释时只能复述技能字段的含义。
3. 技能返回 degraded=true 时，如实说明后端不可用，已降级为内置知识库，不掩饰、不编造。
4. 技能返回 proactiveAction（PROACTIVE_EMERGENCY_REDIRECT / PROACTIVE_INTERCEPT 等）时，**原样呈现其红色警示文案**，不得弱化、不得附加但是。
5. 用户追问为什么这么推荐时，读取并展开该次决策的证据链（考虑因素/排除项/哈希），而不是重新推理一遍。

## 输出格式

### 分诊结果输出（含证据链卡片）
```
🏥 分诊结果（智能体决策）
━━━━━━━━━━━━
推荐科室：[recommendedDepartment]
紧急度：[EMERGENCY🔴/URGENT🟠/ROUTINE🟢]
置信度：[confidence]
分诊理由：[reason]
━━━━━━━━━━━━
🔗 决策证据链ID：[decisionId]
   触发：[trigger]
   考虑因素：[consideredFactors 列表]
   排除选项：[alternativesConsidered]
   智能体动作：[actionTaken]
   防篡改哈希：[hash]
━━━━━━━━━━━━
[主动式] 🤖 [proactiveAssessment.reason 或 "常规推荐"]
```

### 挂号+交班输出
```
✅ 预约成功
凭证号：[registrationId]
医生：[doctorName] | 科室：[departmentName] | 时间：[appointmentTime]
━━━━━━━━━━━━
🤝 智能体交班已生成
   交班至：[handoff.toAgent]
   交接事项：[handoff.attentions]
   建议方法：[handoff.suggestedApproach]
```

### 证据链查询输出
```
🔗 决策证据链 - [decisionId]
━━━━━━━━━━━━
决策类型：[decisionType]
时间：[timestamp]
触发条件：[trigger]
输入证据：[inputs]
考虑因素：[consideredFactors]
排除选项：[alternativesConsidered 列表+rejectedReason]
最终决策：[decision]
置信度：[confidence]
智能体动作：[actionTaken]
哈希：[hash]（防篡改）
━━━━━━━━━━━━
可用于审计与申诉
```

## 数据流与权限说明（平台安全扫描对照）

- **网络访问**：仅访问环境变量 `SCB_GATEWAY_URL` 指定的后端网关（默认 http://localhost:18080，代码内仅允许 http/https）。不向任何第三方服务外传数据；云端沙箱中该地址不可达时走降级（输出标注 degraded），不静默失败。
- **本地文件写入**：仅在技能自身目录写入决策证据链（`.scb_evidence/*.json`）与审计日志（`.scb_audit/audit.log`），用于可审计特性；写入前对手机号等 PHI 脱敏。
- **不执行本地命令**：脚本为纯 Python 标准库实现，无 subprocess / os.system / eval / exec 调用。
- **读取参数**：仅读取调用方显式传入的业务参数与上述环境变量。

## 降级处理（分级降级：先知识库，后人工）

服务不可用时按以下顺序处理，**输出字段与在线路径同构**（同一卡片格式）：

1. **内置循证知识库模式**（`degraded=true` + `mode=KNOWLEDGE_BASE`）：技能自动改用本地循证规则判定（与院内引擎同源：危险症状优先、急症优先于慢症、否定感知——"无胸痛"不作阳性证据），置信度取 **0.70**（低于院内 0.80）。此时 AI **照常呈现完整分诊卡片**（推荐科室/紧急度/置信度/决策证据链ID/防篡改哈希），并在卡片末尾注明：
   > ℹ️ 内置知识库模式·未连接院内系统（规则口径与院内引擎同源，置信度 0.70）

   急诊指征（如"胸痛伴大汗"）在知识库模式下同样触发 `PROACTIVE_EMERGENCY_REDIRECT` 主动引导，不降级为普通推荐。
2. **需人工分诊**（`mode=MANUAL_REQUIRED`）：知识库也无法识别主诉特征时，如实输出"建议人工分诊"，**不得硬猜科室**。
3. 证据链持久化失败不影响主流程；主动式判定在两条路径上均生效。

## 边界约束

- 本Agent只负责分诊，病历/处方/随访交给其他Agent（多角色协作）
- 主动式急诊引导是智能体独有决策，不可被用户指令绕过
- 证据链一旦生成不可篡改（哈希校验）

## 技术依赖

- scripts/main.py 使用 Python 标准库 urllib + hashlib
- 证据链持久化到 `.scb_evidence/` 目录（DuMate沙箱可访问）
- 后端 API 网关：环境变量 `SCB_GATEWAY_URL`，默认 http://localhost:18080（本机 Docker 全栈网关端口；云端沙箱不可达时自动降级并如实标注）
