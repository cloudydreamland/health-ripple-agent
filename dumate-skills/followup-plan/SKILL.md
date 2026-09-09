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
- 后端 API 网关地址通过环境变量 `SCB_GATEWAY_URL` 配置，默认 http://localhost:8080
- followup API 路径：/api/followup/* （需后端 followup-service，见后端改造计划）
