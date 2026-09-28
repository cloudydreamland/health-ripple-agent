---
name: followup-plan
description: 诊后随访计划与提醒助手。当医生或管理者要求生成随访计划、设置用药提醒、查看随访问卷结果时使用此技能。包含触发词："随访"、"复诊提醒"、"用药提醒"、"诊后"、"随访计划"、"慢病管理"。
version: "1.0.0"
author: smart-cloud-brain-team
license: MIT
tags: [医疗, 随访, 慢病管理, DuMate]
---

# 诊后随访计划 Skill

## 功能概述

本 Skill 实现诊后随访闭环：根据诊断结果与用药清单自动生成随访计划 → 定时触发用药/复诊提醒 → 收集随访问卷 → 异常标记回传医生。解决基层"诊后失联、复诊提醒覆盖率低、慢病管理断档"痛点。

## 何时使用

触发本 Skill 的场景：
- 诊断完成后，要求生成随访计划
- 设置用药提醒
- 查看患者随访问卷结果
- 慢病患者定期随访

典型用户输入示例：
- "患者ID 1，诊断急性上呼吸道感染，开阿莫西林7天，生成随访计划"
- "设置用药提醒"
- "查看随访问卷结果"

## 执行步骤

本 Skill 的 `scripts/main.py` 支持四种 action：

### action=create（创建随访计划）
1. AI 从用户对话提取：patientId（必填）、diagnosis（诊断）、medications（用药清单，如"阿莫西林 0.5g 每日三次 7天"）、followupDays（随访天数，默认7）
2. 调用 `python scripts/main.py --action create --patient-id 1 --diagnosis "急性上呼吸道感染" --medications "阿莫西林 0.5g 每日三次 7天" --followup-days 7`
3. 脚本调后端 followup API 创建随访计划
4. AI 输出随访计划卡片

### action=remind（生成用药提醒）
1. AI 根据用药清单生成每日用药提醒内容
2. 调用 `python scripts/main.py --action remind --patient-id 1 --medications "阿莫西林 0.5g 每日三次 7天"`
3. AI 输出提醒计划，由 DuMate 定时任务触发

### action=query（查询随访计划与记录）
1. AI 获取患者ID
2. 调用 `python scripts/main.py --action query --patient-id 1`
3. 输出随访计划与问卷记录

### action=submit（提交随访问卷）
1. AI 收集患者随访问卷回答
2. 调用 `python scripts/main.py --action submit --plan-id 1 --status "好转" --notes "咳嗽减轻"`
3. 若状态为"加重"或"无变化"，AI 自主标记异常回传医生

## AI 自主决策规则

### 随访问卷提交后
- 若患者状态为"加重"或"无变化"，AI 自主标记异常，建议医生关注
- 若状态为"好转"，AI 标记正常，更新随访记录
- 若状态为"痊愈"，AI 建议结束随访计划

### 用药提醒生成
- AI 根据药品频次自动拆分每日提醒时间点
- 提醒内容个性化（药品名+剂量+用法）

## 呈现纪律（给调用方 AI 的输出约束，必须遵守）

1. 按下方《输出格式》**原样呈现**技能返回的字段：科室/紧急度/置信度/decisionId（决策证据链ID）/防篡改哈希/时间学触达等——**不得改写成自己的话，不得省略 decisionId 与哈希**。
2. **不得用自身医学知识扩写或补充技能未返回的内容**（不添加未返回的科室、检查项、用药、疾病推断）；需要解释时只能复述技能字段的含义。
3. 技能返回 degraded=true 时，如实说明后端不可用，已降级为内置知识库，不掩饰、不编造。
4. 技能返回 proactiveAction（PROACTIVE_EMERGENCY_REDIRECT / PROACTIVE_INTERCEPT 等）时，**原样呈现其红色警示文案**，不得弱化、不得附加但是。
5. 用户追问为什么这么推荐时，读取并展开该次决策的证据链（考虑因素/排除项/哈希），而不是重新推理一遍。

## 输出格式

### 随访计划输出格式
```
📋 随访计划已创建
━━━━━━━━━━━━━━
患者ID：[patientId]
诊断：[diagnosis]
随访周期：[followupDays] 天
复诊时间：[followupDate]
用药提醒：[reminderSchedule]
━━━━━━━━━━━━━━
将在 [followupDate] 自动触发随访问询
```

### 用药提醒输出格式
```
💊 用药提醒计划
━━━━━━━━━━━━━━
药品：[drugName] | 剂量：[dosage] | 频次：[frequency] | 疗程：[days]天
每日提醒时间：08:00, 14:00, 20:00
━━━━━━━━━━━━━━
将由 DuMate 定时任务自动触发提醒
```

### 随访问卷结果输出格式
```
📋 随访问卷结果
━━━━━━━━━━━━━━
患者状态：[好转🟢/无变化🟡/加重🔴]
备注：[notes]
━━━━━━━━━━━━━━
[若加重] ⚠️ 已标记异常，已通知医生关注
```

## 数据流与权限说明（平台安全扫描对照）

- **网络访问**：仅访问环境变量 `SCB_GATEWAY_URL` 指定的后端网关（默认 http://localhost:18080，代码内仅允许 http/https）。不向任何第三方服务外传数据；云端沙箱中该地址不可达时走降级（输出标注 degraded），不静默失败。
- **本地文件写入**：仅在技能自身目录写入决策证据链（`.scb_evidence/*.json`）与审计日志（`.scb_audit/audit.log`），用于可审计特性；写入前对手机号等 PHI 脱敏。
- **不执行本地命令**：脚本为纯 Python 标准库实现，无 subprocess / os.system / eval / exec 调用。
- **读取参数**：仅读取调用方显式传入的业务参数与上述环境变量。

## 降级处理

- 若随访计划创建失败，AI 应建议人工安排随访
- 若用药提醒生成失败，AI 应手动输出提醒时间表
- 若随访问卷提交失败，AI 应稍后重试

## 边界约束

- 随访计划创建会真实写入后端数据库
- 用药提醒由 DuMate 定时任务触发，需配置
- 异常标记会回传医生

## 技术依赖

- scripts/main.py 使用 Python 标准库 urllib
- 后端 API 网关地址通过环境变量 `SCB_GATEWAY_URL` 配置，默认 http://localhost:18080（本机 Docker 全栈网关端口；云端沙箱不可达时自动降级并如实标注）
- followup API 路径：/api/followup/* （需后端 followup-service，见后端改造计划）
