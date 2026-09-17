---
name: health-ripple
description: 健康事件涟漪守护智能体。当患者出现新诊断、新开处方、异常指标、出院随访或慢病管理需求时，主动推演该健康事件在患者生活中的多维度连锁影响——药物-生活冲突、复查窗口、并发症早期信号、家属注意事项、医疗时间学触达——生成可解释的涟漪影响图谱；支持疑难病例五Agent多学科会诊（MDT）、健康气象日报与干预回执消解闭环。
metadata:
  version: "1.1.0"
  author: 智慧云脑团队
  tags: [医疗, 健康事件涟漪守护, XAI, 反事实推理, MDT会诊, 医疗时间学]
---

# 健康事件涟漪守护智能体

## 能力概览

本 Skill 提供健康事件的主动式连锁影响推演：

1. **健康事件涟漪效应（Health Event Ripple Effect）** — 一个健康事件触发多维度连锁影响推演，覆盖基层医生无精力逐项推演的盲区。
2. **决策反事实推理（Counterfactual Reasoning）** — 每个高风险涟漪节点生成反事实决策树，记录"如果未识别该冲突会怎样"，全程经反事实护栏审计（FLAGGED 路径仅用于解释与警示，禁止作为建议下发）。
3. **多智能体 MDT 会诊** — 疑难病例触发分诊/处方/病历/随访/涟漪守护五 Agent 多视角发言并收敛为会诊纪要。

全程贯穿**医疗时间学感知**（窗口期 WINDOW / 节律 RHYTHM / 周期 PERIODIC / 季节 SEASONAL）与**主动式守护**（不等用户询问，主动推演事件影响）。

## 何时使用

- 患者完成新诊断后，需要推演该诊断的连锁影响
- 医生新开处方后，需要识别药物-生活冲突、复查窗口
- 患者出现异常指标（如肝功能异常、血糖升高），需推演影响
- 疑难病例（多病共存/诊断不明/治疗矛盾）需要多学科会诊
- 慢病患者出院后需要长期健康事件跟踪
- 家属/患者询问"这个药要注意什么""什么时候复查""出现什么情况要立即就医"

## 执行步骤

### 动作1：涟漪推演（ripple）— 核心能力

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
4. 每个高风险涟漪节点生成反事实决策树（记录"如果未识别该冲突会怎样"）
5. 返回结构化涟漪影响图谱

### 动作2：MDT会诊（mdt）

```
python scripts/main.py --action mdt --patient-id 1 --chief-complaint "胸闷气短3天" --past-history "糖尿病,高血压,慢性肾病"
```

1. 调用 `POST /api/mdt/consult`（后端MDT会诊服务）
2. 后端不可用时降级为内置五Agent视角生成
3. 五Agent多视角发言：分诊/处方/病历/随访/涟漪守护
4. 聚合形成MDT会诊纪要：多视角诊疗建议+风险预警+随访要点+分歧收敛裁决

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

### 动作5：健康气象日报（weather）

```
python scripts/main.py --action weather --patient-id 1
```

1. 调用 `GET /api/health-weather/daily?patientId=`（后端健康气象服务）
2. 用天气隐喻转译当日守护态势：晴（无重点守护）/多云（轻度关注）/大雨（重点守护）/暴雨（高度警戒）
3. 输出今日守护事项（每项携带Timing Card循证卡片）+ 家属提示 + 头条摘要
4. 指数可解释：今日到期触达强度 ÷ 全部活跃触达强度 × 100（每24h内升级就医项+15、未缓解项+10）；天气等级结合今日绝对压力判定
5. 适合每日早晨由 DuMate 定时任务主动播报给患者/家属群

### 动作6：干预回执（feedback）— 涟漪消解闭环

```
python scripts/main.py --action feedback --trigger-id 56 --outcome RESOLVED --patient-id 1 --feedback-note "已按提醒完成复查"
```

1. 调用 `POST /api/chrono/trigger/{id}/feedback?outcome=&note=`
2. outcome三选一：RESOLVED已缓解 / UNRESOLVED未缓解（自动2小时后加强触达）/ ESCALATED已升级就医（终结自动触达，需医生跟进）
3. 返回回执结果 + 患者级涟漪消解率（消解率=已缓解触达强度和÷全部触达强度和）
4. 闭环意义：推演→触达→回执→消解率，让"干预是否有效"可量化

### 动作7：反事实决策树查询（evidence）

```
python scripts/main.py --action evidence --decision-id RIPPLE_DERIVATION-20260915-xxxx
```

查询某涟漪推演决策的反事实决策树与哈希链证据，供审计与申诉。

### 动作8：72小时涟漪预报（forecast）

```
python scripts/main.py --action forecast --patient-id 1
```

1. 调用 `GET /api/health-event/ripple/forecast?patientId=`（后端预报服务）
2. 返回未来72小时逐小时涟漪强度桶 + 峰值时段 + 驱动事件（如"凌晨0-3点低血糖峰"）
3. 确定性可复算：由时间学触达的RII强度按 WINDOW/RHYTHM/PERIODIC/SEASONAL 语义叠加，无随机数
4. 与消解闭环联动：已缓解触达从预报剔除，未缓解触达×1.5加压
5. 用途：医生排今天该管谁、患者知道今晚几点要当心；后端不可用时诚实降级（不臆造预报）

### 动作9：守护价值账本（value）

```
python scripts/main.py --action value
python scripts/main.py --action value --patient-id 1
```

1. 调用 `GET /api/evidence/ledger`（不带参=全量账本，医生视角；带 patientId=单患者）
2. 返回计数型守护动作聚合：审计决策数/高危反事实路径锁定数/黄金窗口触达数/回执缓解数/升级就医转诊数 + 一句话叙事
3. 诚实边界：全部数字来自已入印鉴链的真实决策，不虚构概率换算；后端不可用时诚实降级

## 自主决策规则

DuMate AI 读取脚本输出后，按以下规则自主决策（非仅提示）：

1. **涟漪图谱生成后主动设置时间学触达**
   - 识别到周期性复查窗口（如服药2周复查肝功）→ 主动调用DuMate定时任务设置提醒
   - 识别到节律性事件（如夜间低血糖高发）→ 主动设置凌晨主动询问
   - 识别到窗口期事件（如心梗后2小时）→ 即时高优先级触达患者

2. **高风险冲突主动建议MDT会诊**
   - 涟漪推演识别≥2个HIGH风险冲突节点 → 主动建议医生发起MDT会诊
   - 识别到药物-过敏冲突 → 主动拦截并推送风险通知

3. **反事实决策树可追问**
   - 用户询问"为什么这么决策"→ 读取counterfactualTree字段，解释"如果选了其他方案会怎样"
   - 用户询问某节点详情 → 返回该节点的counterfactualOutcome/riskIfChosen
   - FLAGGED路径仅可用于解释"为什么不能这样做/必须警惕什么"，禁止将其作为可执行建议转述

4. **安全边界自我约束**
   - 能：主动推演涟漪、主动设置时间学触达、主动建议MDT会诊、主动拦截药物冲突
   - 不能：自主开方、自主改变治疗方案、自主下诊断（最终决策权在医生）
   - 每次涟漪/MDT输出必带 `safetyBoundary` 声明块（advisoryOnly/canPrescribe=false/finalDecisionOwner=DOCTOR），供 DuMate AI 与下游系统显式校验

## 安全与合规（Skill 层四重防护）

1. **鉴权**：调用后端网关携带 `Authorization: Bearer`（环境变量 `SCB_API_TOKEN` 或 `--api-token`），无令牌时只走降级模式，不会匿名打穿后端
2. **数据脱敏**：手机号/身份证号在审计日志与本地证据落盘前自动脱敏（`138****78`），防 PHI 明文落盘
3. **防篡改审计**：所有调用写入追加式哈希链审计日志（`.scb_audit/audit.log`，每行含前一行哈希，与后端证据链同构，SHA-256 全长摘要），篡改即刻暴露
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
      {"path": "未识别药物-生活冲突", "counterfactualOutcome": "患者饮酒+二甲双胍→乳酸酸中毒风险", "riskIfChosen": "HIGH", "guardrailVerdict": "FLAGGED"}
    ]
  },
  "mdtConsultation": {"...": "..."},
  "evidenceChain": {"decisionId": "RIPPLE_DERIVATION-...", "hash": "SHA-256", "prevHash": "..."}
}
```

## 典型场景

医生："患者新诊断2型糖尿病，已开二甲双胍，推演健康事件涟漪"

智能体：[触发涟漪守护Skill] → 推演5维度涟漪图谱 → 每节点反事实决策树（护栏审计后FLAGGED锁定）→ 主动设置DuMate定时触达（服药2周复查/夜间低血糖询问/换季血糖监测）→ 返回涟漪图谱卡片
