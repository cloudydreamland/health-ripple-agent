# 智慧云脑·健康事件涟漪守护智能体

> **Health Ripple Agent** — 当新诊断、新处方、异常指标发生，智能体主动推演该健康事件在患者生活中的多维连锁影响。
>
> 2026年iCAN大学生创新创业大赛 · AI无代码智能体挑战赛 · DuMate智能体应用创新赛题（医疗健康场景）

**一句话定位：** 市面产品解决"诊断准不准、随访做没做"，本作品解决**"健康事件的连锁影响如何推演、干预时机如何计算、责任证据如何固化"**——从点状AI工具跨入事件级健康操作系统。

## 三大技术支柱

| 支柱 | 能力 | 竞品验证 |
|---|---|---|
| **医疗时间学引擎** | 窗口期/节律/周期/季节四类可计算触达规则，每项携带 **Timing Card 循证卡片**（指南依据+错过代价+证据等级） | 30+竞品交叉验证：无一家把时间语义做成触达引擎 |
| **反事实信任链** | 决策级反事实推理 + **Counterfactual Guardrail 护栏**（高危路径FLAGGED锁定）+ 哈希链防篡改存证 + **HL7 FHIR R4 Provenance 标准导出** | XAI文献中反事实仅占13.5%；无一家对AI决策做密码学存证 |
| **五Agent学科化MDT** | 分诊/处方/病历/随访/涟漪守护五Agent以专科身份辩论收敛，输出会诊纪要 | 现有AI-MDT均为AI辅助组织真人开会，Agent自主会诊为代际差异 |

## 仓库结构

```
dumate-skills/                 # 5个DuMate Skill（Agent Skills开放标准）
├── medical-triage/            #   智能分诊（主动式急诊判定+证据链）
├── medical-record-draft/      #   病历草稿生成
├── prescription-safety/       #   处方安全审核（HIGH风险自主拦截）
├── followup-plan/             #   诊后随访计划
└── health-ripple/             #   健康事件涟漪守护（核心创新，含安全冒烟测试）

smart_cloud_brain/             # 后端工具层（Spring Boot 3 微服务，Docker Compose 20容器）
├── backend/ripple-service/    #   涟漪推演/MDT/时间学引擎/反事实护栏/证据链FHIR导出
├── backend/followup-service/  #   诊后随访服务
├── scripts/e2e_test.py        #   端到端测试（19步全旅程）
└── deploy/                    #   Docker Compose 一键部署

作品说明文档.md                # 完整方案（含30+竞品硬核对比）
应用方案-PDF内容.md            # 提交用20页PDF源稿
演示脚本.md                    # DuMate演示录屏剧本（3指令链+8高光帧）
开发进度文档.md / 开发计划文档.md
```

## 快速开始

```bash
# 1. 启动后端全栈（20容器：13微服务+KingbaseES+RabbitMQ）
cd smart_cloud_brain
docker compose -f deploy/docker-compose.yml up -d

# 2. 运行端到端测试（19步：注册→分诊→挂号→病历→处方→随访→涟漪→MDT→证据审计）
py -3 scripts/e2e_test.py

# 3. Skill安全冒烟测试（5用例：降级安全/超长拒绝/审计脱敏/证据脱敏/MDT边界）
cd ../dumate-skills/health-ripple
py -3 scripts/test_security_smoke.py
```

## 质量背书（全部自动化测试）

- **E2E 19/19 通过**：完整患者旅程，含 Timing Card / 反事实护栏 / FHIR Provenance 导出验证
- **后端单测 4/4 通过**：五维图谱 / 反事实持久化 / 哈希链防篡改（篡改即验链失败）/ 时间学调度
- **Skill安全冒烟 5/5 通过**：鉴权 / PHI脱敏 / 哈希链审计 / 输入校验 / 安全边界

## 核心演示场景

> 医生："患者新诊断2型糖尿病，已开二甲双胍，推演健康事件涟漪"

智能体推演出**五维涟漪图谱**：药物-生活冲突（禁酒/造影剂停药48h）→ 复查窗口（2周肝肾功/3月糖化）→ 并发症信号（视力模糊→立即眼科）→ 家属注意 → 时间学触达（夜间0-3点低血糖节律守护），生成 **16条反事实路径**，护栏审计**锁定9条高危**，全程证据入哈希链并可导出FHIR标准格式。

---

*安全边界：智能体能主动拦截风险处方、主动推演涟漪、主动触达随访；不能自主开方、下诊断、改变治疗方案——终审权在医生。*
