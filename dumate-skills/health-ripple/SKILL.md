---
name: health-ripple
description: 健康事件涟漪守护智能体。当患者新诊断、新开处方、出现异常指标、出院、慢病管理、需要推演健康事件连锁影响、多学科会诊时使用。一个健康事件触发智能体主动推演药物-生活冲突、复查窗口、并发症早期信号、家属注意事项、医疗时间学触达等多维度连锁影响，生成涟漪影响图谱。包含"涟漪"、"推演"、"连锁影响"、"MDT会诊"、"多学科"、"健康事件"、"并发症"、"复查窗口"、"药物冲突"、"用药注意"等触发词。
version: "1.0.0"
author: 智慧云脑团队
tags: [医疗, 健康事件涟漪守护, XAI, 反事实推理, MDT会诊, 医疗时间学]
---

# 健康事件涟漪守护智能体（核心创新 Skill）

## 核心创新点（不人云亦云）

本 Skill 是作品**六大差异化创新**的核心载体，承载其中三大新创新：

1. **健康事件涟漪效应（Health Event Ripple Effect）** — 一个健康事件触发智能体主动推演多维度连锁影响，覆盖基层医生无精力推演的盲区。**没有任何医疗AI产品做涟漪效应**。
2. **决策反事实推理（Counterfactual Reasoning）** — 每个涟漪节点生成反事实决策树，记录"如果未识别该冲突会怎样"，命中XAI学术前沿。
3. **多智能体MDT会诊** — 疑难病例触发五Agent多视角发言，模拟真实医疗多学科会诊文化。

并贯穿**医疗时间学感知**（窗口期/节律/周期/季节）与**主动式智能体**（不等患者询问，主动推演事件影响）。

## 何时使用

- 患者完成新诊断后，需要推演该诊断的连锁影响
- 医生新开处方后，需要识别药物-生活冲突、复查窗口
- 患者出现异常指标（如肝功能异常、血糖升高），需推演影响
- 疑难病例（多病共存/诊断不明/治疗矛盾）需要多学科会诊
- 慢病患者出院后需要长期健康事件跟踪
- 用户提及"涟漪"、"推演"、"连锁影响"、"MDT会诊"、"多学科"、"并发症"、"复查窗口"、"用药注意"

## 执行步骤

### 动作1：涟漪推演（ripple）— 核心高光演示

```
python scripts/main.py --action ripple --diagnosis "2型糖尿病" --drugs '[{"drugName":"二甲双胍"}]' --patient-id 1
```

1. 调用 `POST /api/health-event/ripple`（后端涟漪推演服务）
2. 后端不可用时降级为内置知识库推演（DRUG_LIFESTYLE_CONFLICTS / COMPLICATION_SIGNALS / RECHECK_WINDOWS / CHRONO_EVENTS）
3. 推演5个维度涟漪影响：
   - 药物-生活冲突维度（服某药不能吃柚子/晒太阳/开车/饮酒）
   - 复查窗口维度（何时查什么，医疗时间学·周期性）
   - 并发症早期信号维度（出现什么症状立即就医）
   - 家属注意事项维度
   - 时间学触达维度（窗口期/节律/周期/季节）
4. 每个涟漪节点生成反事实决策树（记录"如果未识别该冲突会怎样"）
5. 返回结构化涟漪影响图谱

### 动作2：MDT会诊（mdt）— 核心创新

```
python scripts/main.py --action mdt --patient-id 1 --chief-complaint "胸闷气短3天" --past-history "糖尿病,高血压,慢性肾病"
```

1. 调用 `POST /api/mdt/consult`（后端MDT会诊服务）
2. 后端不可用时降级为内置五Agent视角生成
3. 五Agent多视角发言：分诊/处方/病历/随访/涟漪守护
4. 聚合形成MDT会诊纪要：多视角诊疗建议+风险预警+随访要点

### 动作3：药物-生活冲突查询（conflict）

```
python scripts/main.py --action conflict --drug "华法林"
```

查询某药物的-生活冲突知识库。

### 动作4：并发症信号查询（complication）

```
python scripts/main.py --action complication --diagnosis "2型糖尿病"
```

查询某诊断的并发症早期信号。

### 动作5：反事实决策树查询（evidence）

```
python scripts/main.py --action evidence --decision-id RIPPLE-2026-001
```

查询某涟漪推演决策的反事实决策树，供审计与申诉。

## AI 自主决策规则（SKILL.md 明确指令）

DuMate AI 读取脚本输出后，按以下规则自主决策（非仅提示）：

1. **涟漪图谱生成后主动设置时间学触达**
   - 识别到周期性复查窗口（如服药2周复查肝功）→ 主动调用DuMate定时任务设置提醒
   - 识别到节律性事件（如夜间低血糖高发）→ 主动设置凌晨主动询问
   - 识别到窗口期事件（如心梗后2小时）→ 即时高优先级触达患者

2. **高风险冲突主动建议MDT会诊**
   - 涟漪推演识别≥2个HIGH风险冲突节点 → AI主动建议医生发起MDT会诊
   - 识别到药物-过敏冲突 → AI主动拦截并推送风险通知

3. **反事实决策树可追问**
   - 用户询问"为什么这么决策"→ AI读取counterfactualTree字段，解释"如果选了其他方案会怎样"
   - 用户询问某节点详情 → AI返回该节点的counterfactualOutcome/riskIfChosen

4. **安全边界自我约束**
   - 能：主动推演涟漪、主动设置时间学触达、主动建议MDT会诊、主动拦截药物冲突
   - 不能：自主开方、自主改变治疗方案、自主下诊断（最终决策权在医生）
   - 每次涟漪/MDT输出必带 `safetyBoundary` 声明块（advisoryOnly/canPrescribe=false/finalDecisionOwner=DOCTOR），供 DuMate AI 与下游系统显式校验

## 安全与合规（Skill 层四重防护）

1. **鉴权**：调用后端网关携带 `Authorization: Bearer`（环境变量 `SCB_API_TOKEN` 或 `--api-token`），无令牌时只走降级模式，不会匿名打穿后端
2. **数据脱敏**：手机号/身份证号在审计日志与本地证据落盘前自动脱敏（`138****78`），防 PHI 明文落盘
3. **防篡改审计**：所有调用写入追加式哈希链审计日志（`.scb_audit/audit.log`，每行含前一行哈希，与后端证据链同构），篡改即刻暴露
4. **输入校验**：诊断/主诉/药品等字段长度上限 + JSON 数组类型校验，防提示注入式超长载荷；网关地址仅允许 http/https

验证：`py -3 scripts/test_security_smoke.py`（5 个安全用例：降级推演+安全边界/超长拒绝/审计链脱敏/证据脱敏/MDT边界）

## 输出格式

涟漪推演输出标准JSON，供DuMate AI读取并按上述规则自主决策：

```json
{
  "rippleGraph": {
    "healthEvent": {"diagnosis": "2型糖尿病", "drugs": ["二甲双胍"]},
    "dimensions": {
      "drugLifestyleConflicts": [...],
      "recheckWindows": [...],
      "complicationSignals": [...],
      "familyAttentions": [...],
      "chronoTriggers": [...]
    }
  },
  "counterfactualTree": {
    "chosenPath": "推演健康事件涟漪影响并生成守护计划",
    "alternativePaths": [
      {"path": "未识别药物-生活冲突", "counterfactualOutcome": "患者饮酒+二甲双胍→乳酸酸中毒风险", "riskIfChosen": "HIGH"}
    ]
  },
  "mdtConsultation": {...},
  "evidenceChain": {...}
}
```

## 演示高光场景

医生："患者新诊断2型糖尿病，已开二甲双胍，推演健康事件涟漪"

智能体：[触发涟漪守护Skill] → 推演5维度涟漪图谱 → 每节点反事实决策树 → 主动设置DuMate定时触达（服药2周复查/夜间低血糖询问/换季血糖监测）→ 返回涟漪图谱卡片
