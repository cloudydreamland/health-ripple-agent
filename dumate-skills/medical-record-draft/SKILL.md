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
- "挂号ID 1，对话：患者诉发热咳嗽3天，伴有胸闷…"
- "生成病历"
- "诊断改为急性上呼吸道感染，确认保存"

## 执行步骤

本 Skill 的 `scripts/main.py` 支持三种 action：

### action=generate（生成病历草稿）
1. AI 从用户对话提取：registrationId（挂号ID，必填）、dialogueText（问诊对话文本，必填）、departmentCode（科室代码，可选）
2. 调用 `python scripts/main.py --action generate --registration-id 1 --dialogue-text "患者诉发热咳嗽3天…" --department-code "RES"`
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
2. 调用 `python scripts/main.py --action save --registration-id 1 --chief-complaint "发热咳嗽3天" --diagnosis "急性上呼吸道感染" --present-illness "..." --treatment-advice "..." --ai-generated true`
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

## 降级处理

- 若病历生成失败，AI 应建议医生手动编写
- 若历史病历查询失败，AI 应告知"无历史病历或查询失败"
- 若保存失败，AI 应提示稍后重试

## 边界约束

- 病历草稿必须经医生确认后方可保存
- 保存病历会真实写入后端数据库
- AI 生成的病历标注 aiGenerated=true

## 技术依赖

- scripts/main.py 使用 Python 标准库 urllib
- 后端 API 网关地址通过环境变量 `SCB_GATEWAY_URL` 配置，默认 http://localhost:8080
