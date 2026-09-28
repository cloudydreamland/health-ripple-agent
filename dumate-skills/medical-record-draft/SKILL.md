---
name: medical-record-draft
description: 病历草稿生成助手。当医生要求生成病历、输入问诊对话文本、要求查看或保存病历时使用此技能。包含触发词："病历"、"问诊记录"、"生成病历"、"保存病历"、"主诉"、"现病史"。
version: "1.0.0"
author: smart-cloud-brain-team
license: MIT
tags: [医疗, 病历, AI生成, DuMate]
---

# 病历草稿生成 Skill

## 功能概述

本 Skill 实现病历草稿生成全流程：医生口述/键入问诊对话 → AI 病历生成工作流输出结构化病历草稿 → 医生审核确认 → 保存病历。解决基层"病历耗时、结构化程度低、关键要素遗漏"痛点。

## 何时使用

触发本 Skill 的场景：
- 医生要求根据问诊对话生成病历草稿
- 医生要求查看某患者的历史病历
- 医生确认病历草稿并要求保存

典型用户输入示例：
- "挂号ID 6668，对话：患者诉多饮多尿1月伴胸闷，诊断2型糖尿病…"（挂号ID必须取自真实挂号单，来自分诊链的挂号步骤或医生工作站的在诊列表）
- "生成病历"
- "诊断改为急性上呼吸道感染，确认保存"

## 执行步骤

本 Skill 的 `scripts/main.py` 支持三种 action：

### action=generate（生成病历草稿）
1. AI 从用户对话提取：registrationId（挂号ID，必填）、dialogueText（问诊对话文本，必填）、departmentCode（科室代码，可选）
2. 调用 `python scripts/main.py --action generate --registration-id <真实挂号ID> --dialogue-text "患者诉发热咳嗽3天…" --department-code "RES"`（registrationId 必须是挂号服务中真实存在的单号，示例值 6668 为演示环境已验证单号）
3. 脚本内部调 `/api/medical-record/generate`，后端组装患者/医生信息后调 Dify 病历工作流
4. AI 读取输出 JSON：主诉/现病史/既往史/查体/诊断/处理建议（结构化）
5. AI 组织为病历草稿卡片，标注"草稿待医生确认"

### action=history（查询历史病历）
1. AI 从用户对话获取 patientId
2. 调用 `python scripts/main.py --action history --patient-id 1`
3. 脚本调 `/internal/medical-records/patient/{id}` 获取历史病历
4. AI 输出历史病历列表

### action=save（保存病历）
1. 医生审核修改病历后，AI 提取：registrationId、chiefComplaint、diagnosis、各病历字段
2. 调用 `python scripts/main.py --action save --registration-id <真实挂号ID> --chief-complaint "发热咳嗽3天" --diagnosis "急性上呼吸道感染" --present-illness "..." --treatment-advice "..." --ai-generated true`
3. 脚本调 `/api/medical-record/save` 保存病历
4. AI 输出保存成功结果

## AI 自主决策规则

### 生成病历后
- AI 必须标注"草稿待医生确认"，不可自动保存
- AI 应主动提示医生审核关键要素（主诉、诊断、处理建议）
- 若 AI 生成结果返回 degraded=true，AI 应标注"AI 降级，建议人工编写"

### 保存病历前
- AI 必须确认医生已明确"确认保存"指令
- 若医生未确认，AI 应提示"请确认后保存"
- 保存是真实写操作，需谨慎

## 呈现纪律（给调用方 AI 的输出约束，必须遵守）

1. 按下方《输出格式》**原样呈现**技能返回的字段：科室/紧急度/置信度/decisionId（决策证据链ID）/防篡改哈希/时间学触达等——**不得改写成自己的话，不得省略 decisionId 与哈希**。
2. **不得用自身医学知识扩写或补充技能未返回的内容**（不添加未返回的科室、检查项、用药、疾病推断）；需要解释时只能复述技能字段的含义。
3. 技能返回 degraded=true 时，如实说明后端不可用，已降级为内置知识库，不掩饰、不编造。
4. 技能返回 proactiveAction（PROACTIVE_EMERGENCY_REDIRECT / PROACTIVE_INTERCEPT 等）时，**原样呈现其红色警示文案**，不得弱化、不得附加但是。
5. 用户追问为什么这么推荐时，读取并展开该次决策的证据链（考虑因素/排除项/哈希），而不是重新推理一遍。

## 输出格式

### 病历草稿输出格式
```
📋 病历草稿（待医生确认）
━━━━━━━━━━━━━━
挂号ID：[registrationId]
主诉：[chiefComplaint]
现病史：[presentIllness]
既往史：[pastHistory]
查体：[physicalExam]
诊断：[diagnosis]
处理建议：[treatmentAdvice]
━━━━━━━━━━━━━━
⚠️ 此为 AI 生成的病历草稿，请医生审核修改后回复"确认保存"
[如降级] ⚠️ AI 降级提示，建议人工编写病历
```

### 历史病历输出格式
```
📚 历史病历列表
━━━━━━━━━━━━━━
[病历ID] | [就诊时间] | [诊断]
...
```

### 保存结果输出格式
```
✅ 病历保存成功
病历ID：[medicalRecordId]
诊断：[diagnosis]
```

## 数据流与权限说明（平台安全扫描对照）

- **网络访问**：仅访问环境变量 `SCB_GATEWAY_URL` 指定的后端网关（默认 http://localhost:18080，代码内仅允许 http/https）。不向任何第三方服务外传数据；云端沙箱中该地址不可达时走降级（输出标注 degraded），不静默失败。
- **本地文件写入**：仅在技能自身目录写入决策证据链（`.scb_evidence/*.json`）与审计日志（`.scb_audit/audit.log`），用于可审计特性；写入前对手机号等 PHI 脱敏。
- **不执行本地命令**：脚本为纯 Python 标准库实现，无 subprocess / os.system / eval / exec 调用。
- **读取参数**：仅读取调用方显式传入的业务参数与上述环境变量。

## 降级处理（分级降级：先知识库，后人工）

1. **内置知识库模式**（`degraded=true` + `mode=KNOWLEDGE_BASE`）：未连接院内病历服务时，技能自动从问诊对话中**确定性提取**病历要素（主诉/现病史/诊断/处理建议），规则与院内提取口径同源：诊断按关键词表归一（如"糖尿病"→"2型糖尿病"）、药品按词表提取、主诉按"诉/主诉"切分。此时 AI **照常呈现病历草稿卡片**并注明：
   > ℹ️ 内置知识库模式·未连接院内系统（仅提取对话中明确出现的内容）
   - 对话中**未提及诊断**时，诊断字段如实输出"待医生明确诊断（知识库未从对话中识别出诊断关键词）"，**绝不臆造诊断**；
   - 查体等缺失项如实标注"待医生补充"。
2. 若历史病历查询失败，AI 应告知"无历史病历或查询失败"；若保存失败，AI 应提示稍后重试。
3. 任何模式下草稿都必须经医生确认后方可保存（不变）。

## 边界约束

- 病历草稿必须经医生确认后方可保存
- 保存病历会真实写入后端数据库
- AI 生成的病历标注 aiGenerated=true

## 技术依赖

- scripts/main.py 使用 Python 标准库 urllib
- 后端 API 网关地址通过环境变量 `SCB_GATEWAY_URL` 配置，默认 http://localhost:18080（本机 Docker 全栈网关端口；云端沙箱不可达时自动降级并如实标注）
