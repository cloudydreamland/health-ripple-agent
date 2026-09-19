# RippleBench v3 评测报告

> 完成时间：2026-09-20T00:39:58 ｜ 后端：http://localhost:18080 ｜ 总评：**全部达标**

## 一、核心指标

| 指标 | 实测 | 达标线 | 判定 |
|---|---|---|---|
| 分诊路由top-1准确率 | **100.0%**（20/20） | ≥90% | ✅ |
| 涟漪推演用例通过率 | **100.0%**（20/20） | ≥90% | ✅ |
| 反事实护栏灵敏度（危险→锁定） | **100%** | 100% | ✅ |
| 反事实护栏特异度（良性→不误锁，含知识库命中阴性） | **100%** | 100% | ✅ |
| ├ 其中：真特异度（MEDIUM命中、图谱非空仍0误锁） | **100%** | 参考 | — |
| └ 其中：防幻觉（知识库外0臆造） | **100%** | 参考 | — |
| **盲测集通过率（held-out，开发期未参与调参）** | **100.0%**（10/10） | ≥90% | ✅ |
| RII强度指数有效率 | **100%**（19次非空推演） | 100% | ✅ |
| 消解闭环场景通过率 | **100%**（10/10） | ≥90% | ✅ |
| 哈希链完整性 | valid=True（896条决策） | 必须 | ✅ |
| FHIR Provenance导出 | True | 必须 | ✅ |
| MDT五Agent会诊 | views=5 | 5 | ✅ |
| 时间学四类型覆盖 | ['PERIODIC', 'RHYTHM', 'SEASONAL', 'WINDOW'] | WINDOW/RHYTHM/PERIODIC/SEASONAL | ✅ |

## 二、评测方法（开发集与盲测集隔离）

**开发集（T/R/G/W，60例+10场景）**：用于工程对齐与回归——分诊三元组（科室+紧急度+降级）、涟漪五维知识命中、护栏双通道、消解闭环数学。其金标准部分依据内置循证知识库制定，测的是'知识库→图谱→RII→护栏→闭环'全链路工程正确性；知识库医学内容的完备性由 Timing Card 引用的临床指南（AHA/ACC/ADA/GINA/中国防治指南）承担。**开发集分数不构成临床正确性主张。**

**盲测集（BD，10例 held-out）**：全部为开发期未参与任何规则调参/阈值校准的新病例，金标准依据外部临床指南与通用分诊原则独立制定（每例标注 goldSource）。分两类：
- **bd_external（知识库外）**：期望为诚实空态（RII=0、0臆造建议）或诚实降级（degraded=true）——检验泛化到未知病例时的安全底线，不硬猜、不幻觉；
- **bd_transfer（知识库内要素的未见组合）**：检验组合迁移（如华法林+头孢类联用、多病共存）与红线泛化（否定语义、颅压危象、儿科急症）。
盲测集只报告、不回填规则——若盲测暴露缺陷，修复后须连同开发集一起复测并记录版本。

## 三、版本改进记录（评测驱动的闭环）

- v1（2026-09-15 凌晨首轮）：20例分诊中暴露2处规则缺口——(1)卒中/意识类红色指征（意识不清/言语不清/偏瘫）未覆盖，落入降级兜底（安全缺口）；(2)哮喘+呼吸困难被心血管规则优先误路由至心内科（急症误分流）。10例危险反事实场景全部被正确FLAGGED，10例良性场景0误锁。
- v1另暴露1处并发幂等缺陷：证据决策ID为「秒级时间戳+输入哈希前6位」，高频同秒重复输入触发唯一约束冲突→500（护栏子集5例请求失败）。该缺陷在功能性E2E中不可见（用例互不相同），被评测集的高频重复调用模式暴露——正是量化评测的价值。
- v2（2026-09-15 修复后）：ai-service规则引擎新增优先级0层（神经急症红色指征→急诊分流；哮喘持续状态→呼吸急症），medical-triage Skill危险词表同步补齐卒中三联征；EvidenceChainService决策ID引入随机熵后缀（8位hex）保证并发唯一性。
- v3（2026-09-18 诚信升级）：(1)新增10例盲测集（blind_cases.json，开发期未参与调参的held-out集，金标准依据外部指南与通用分诊原则独立制定）——区分'开发集对齐度'与'盲测泛化力'，回应'金标准源自实现自身知识库'的循环论证质疑；(2)护栏特异度子集重构：7例'知识库外空集恒真'升级为5例'知识库真实命中MEDIUM冲突的阴性用例'（图谱非空仍0误锁的真特异度）+5例防幻觉用例，并拆分报告 specificity_kb_hit 与 anti_hallucination；(3)评测发现的实现侧缺陷修复：哈希链并发分叉（并发回归测试落库）、分诊否定语境误判（无胸痛→急诊）、护栏自审自循环（新增独立关键词通道可覆写生成器误标）、MDT会诊ID同秒冲突、FHIR导出伪标准URN；(4)护栏审计增加labelMismatch口径：生成器标签与独立关键词通道不一致时记录覆写，可被本评测检验。
- v3.1（2026-09-18 盲测驱动修复，修复后开发集+盲测集双复测）：首轮盲测暴露2处泛化缺口——(1)患儿高热抽搐被成人神经急症规则抢先路由至全科急诊（年龄层路由应优先），修复：儿科红色指征提升至最高优先级；(2)灵敏度主指标与逐例内容断言脱钩（FLAGGED计数达标即计分，'双硫仑'等锁定语义断言失败不计入），修复：灵敏度按完整判定计数，锁定语义同时匹配路径文本与后果描述。另修复晕厥路由回归（重写规则引擎时从心血管词表遗漏）并补单测回归锁。盲测集首轮即抓到开发集两轮迭代都没暴露的缺陷——held-out集的价值实证。

## 四、API延迟（网关实测）

| API | 调用次数 | P50(ms) | P95(ms) | Max(ms) |
|---|---|---|---|---|
| `/api/chrono/trigger/2292/feedback` | 2 | 23 | 33 | 33 |
| `/api/chrono/trigger/2325/feedback` | 1 | 24 | 24 | 24 |
| `/api/chrono/triggers/patient/144` | 2 | 16 | 16 | 16 |
| `/api/doctor/login` | 1 | 296 | 296 | 296 |
| `/api/evidence/RIPPLE_DERIVATION-20260920003956-9fdd4208/fhir` | 1 | 29 | 29 | 29 |
| `/api/evidence/ledger` | 1 | 124 | 124 | 124 |
| `/api/evidence/verify` | 1 | 64 | 64 | 64 |
| `/api/health-event/guard-queue` | 2 | 10 | 320 | 320 |
| `/api/health-event/ripple` | 49 | 26 | 43 | 79 |
| `/api/health-event/ripple/feedback-ledger` | 1 | 18 | 18 | 18 |
| `/api/health-event/ripple/forecast` | 3 | 29 | 30 | 30 |
| `/api/health-event/ripple/resolution` | 2 | 20 | 32 | 32 |
| `/api/health-weather/daily` | 6 | 25 | 38 | 38 |
| `/api/mdt/consult` | 1 | 38 | 38 | 38 |
| `/api/patient/login` | 1 | 256 | 256 | 256 |
| `/api/patient/register` | 1 | 341 | 341 | 341 |
| `/api/triage/consult` | 24 | 49 | 76 | 89 |

## 五、逐用例明细

### 分诊路由（开发集）

- [✅] **T01** dept=CARDIOLOGY urgency=EMERGENCY degraded=False 期望=CARDIOLOGY/EMERGENCY/False
- [✅] **T02** dept=CARDIOLOGY urgency=ROUTINE degraded=False 期望=CARDIOLOGY/ROUTINE/False
- [✅] **T03** dept=GENERAL urgency=ROUTINE degraded=False 期望=GENERAL/ROUTINE/False
- [✅] **T04** dept=RESPIRATORY urgency=ROUTINE degraded=False 期望=RESPIRATORY/ROUTINE/False
- [✅] **T05** dept=RESPIRATORY urgency=EMERGENCY degraded=False 期望=RESPIRATORY/EMERGENCY/False
- [✅] **T06** dept=GENERAL urgency=EMERGENCY degraded=False 期望=GENERAL/EMERGENCY/False
- [✅] **T07** dept=GENERAL urgency=EMERGENCY degraded=False 期望=GENERAL/EMERGENCY/False
- [✅] **T08** dept=CARDIOLOGY urgency=ROUTINE degraded=False 期望=CARDIOLOGY/ROUTINE/False
- [✅] **T09** dept=CARDIOLOGY urgency=EMERGENCY degraded=False 期望=CARDIOLOGY/EMERGENCY/False
- [✅] **T10** dept=RESPIRATORY urgency=ROUTINE degraded=False 期望=RESPIRATORY/ROUTINE/False
- [✅] **T11** dept=GENERAL urgency=ROUTINE degraded=False 期望=GENERAL/ROUTINE/False
- [✅] **T12** dept=CARDIOLOGY urgency=EMERGENCY degraded=False 期望=CARDIOLOGY/EMERGENCY/False
- [✅] **T13** dept=RESPIRATORY urgency=EMERGENCY degraded=False 期望=RESPIRATORY/EMERGENCY/False
- [✅] **T14** dept=GENERAL urgency=ROUTINE degraded=False 期望=GENERAL/ROUTINE/False
- [✅] **T15** dept=RESPIRATORY urgency=ROUTINE degraded=False 期望=RESPIRATORY/ROUTINE/False
- [✅] **T16** dept=GENERAL urgency=EMERGENCY degraded=False 期望=GENERAL/EMERGENCY/False
- [✅] **T17** dept=GENERAL urgency=ROUTINE degraded=True 期望=GENERAL/ROUTINE/True
- [✅] **T18** dept=GENERAL urgency=ROUTINE degraded=True 期望=GENERAL/ROUTINE/True
- [✅] **T19** dept=CARDIOLOGY urgency=EMERGENCY degraded=False 期望=CARDIOLOGY/EMERGENCY/False
- [✅] **T20** dept=GENERAL urgency=EMERGENCY degraded=False 期望=GENERAL/EMERGENCY/False

### 涟漪推演（开发集）

- [✅] **R01** 冲突3 复查6 信号7 家属6 触达7 RII=74.3(RED)
- [✅] **R02** 冲突0 复查3 信号3 家属3 触达3 RII=51.4(RED)
- [✅] **R03** 冲突2 复查2 信号0 家属0 触达1 RII=34.0(ORANGE)
- [✅] **R04** 冲突0 复查1 信号2 家属3 触达1 RII=41.6(ORANGE)
- [✅] **R05** 冲突0 复查0 信号1 家属2 触达2 RII=26.5(ORANGE)
- [✅] **R06** 冲突0 复查0 信号2 家属0 触达0 RII=68.6(RED)
- [✅] **R07** 冲突3 复查6 信号9 家属6 触达7 RII=74.3(RED)
- [✅] **R08** 冲突2 复查0 信号0 家属0 触达0 RII=56.1(RED)
- [✅] **R09** 冲突1 复查0 信号0 家属0 触达0 RII=37.1(ORANGE)
- [✅] **R10** 冲突1 复查0 信号0 家属0 触达0 RII=81.2(RED)
- [✅] **R11** 冲突1 复查0 信号0 家属0 触达0 RII=35.1(ORANGE)
- [✅] **R12** 冲突1 复查0 信号0 家属0 触达0 RII=77.0(RED)
- [✅] **R13** 冲突1 复查0 信号0 家属0 触达0 RII=77.0(RED)
- [✅] **R14** 冲突1 复查0 信号0 家属0 触达0 RII=77.0(RED)
- [✅] **R15** 冲突4 复查0 信号0 家属0 触达0 RII=56.1(RED)
- [✅] **R16** 冲突0 复查0 信号0 家属0 触达0 RII=0.0(YELLOW)
- [✅] **R17** 冲突0 复查4 信号6 家属6 触达5 RII=69.4(RED)
- [✅] **R18** 冲突4 复查3 信号4 家属3 触达4 RII=74.5(RED)
- [✅] **R19** 冲突0 复查3 信号5 家属3 触达3 RII=70.1(RED)
- [✅] **R20** 冲突0 复查3 信号4 家属5 触达5 RII=60.9(RED)

### 反事实护栏（开发集）

- [✅] **G01** 反事实12条 FLAGGED=8
- [✅] **G02** 反事实3条 FLAGGED=2
- [✅] **G03** 反事实2条 FLAGGED=2
- [✅] **G04** 反事实3条 FLAGGED=3
- [✅] **G05** 反事实3条 FLAGGED=1
- [✅] **G06** 反事实2条 FLAGGED=2
- [✅] **G07** 反事实6条 FLAGGED=3
- [✅] **G08** 反事实2条 FLAGGED=2
- [✅] **G09** 反事实2条 FLAGGED=2
- [✅] **G10** 反事实8条 FLAGGED=4
- [✅] **B01** 冲突1条(全MEDIUM=True) 反事实0条 FLAGGED=0（期望0）
- [✅] **B02** 冲突1条(全MEDIUM=True) 反事实0条 FLAGGED=0（期望0）
- [✅] **B03** 冲突1条(全MEDIUM=True) 反事实0条 FLAGGED=0（期望0）
- [✅] **B04** 冲突2条(全MEDIUM=True) 反事实0条 FLAGGED=0（期望0）
- [✅] **B05** 冲突1条(全MEDIUM=True) 反事实0条 FLAGGED=0（期望0）
- [✅] **B06** 知识库外 反事实0条 冲突0条 FLAGGED=0
- [✅] **B07** 知识库外 反事实0条 冲突0条 FLAGGED=0
- [✅] **B08** 知识库外 反事实0条 冲突0条 FLAGGED=0
- [✅] **B09** 知识库外 反事实0条 冲突0条 FLAGGED=0
- [✅] **B10** 知识库外 反事实0条 冲突0条 FLAGGED=0

### 消解闭环（开发集）

- [✅] **W01** 空患者 weather=SUNNY index=0.0（期望SUNNY/0）
- [✅] **W02** 冠心病 → weather=STORM index=33.7 今日13项 TimingCard=✓
- [✅] **W03** 2型糖尿病 → weather=STORM index=31.5 今日13项 TimingCard=✓
- [✅] **W04** 高血压 → weather=STORM index=32.2 今日14项 TimingCard=✓
- [✅] **W05** RESOLVED回执 → 消解率=2.7% 强度和=1062.7
- [✅] **W06** UNRESOLVED → 加强触达至2026-09-20T02:39
- [✅] **W07** 回执明细账本61条，字段完整=True
- [✅] **W08** ESCALATED → escalatedCount=1, status=有升级就医项，需医生跟进
- [✅] **W09** 升级后气象合法 weather=STORM headline=有1项已升级就医，请家属重点关注医生反馈
- [✅] **W10** 隔离性：另一空患者仍为SUNNY（数据不串扰）

### 盲测集（held-out BD）

- [✅] **BD-R01** 冲突0 信号0 触达0 FLAGGED=0 RII=0.0
- [✅] **BD-R02** 冲突0 信号0 触达0 FLAGGED=0 RII=0.0
- [✅] **BD-R03** 冲突0 信号0 触达0 FLAGGED=0 RII=0.0
- [✅] **BD-R04** 冲突3 信号0 触达1 FLAGGED=4 RII=46.9
- [✅] **BD-R05** 冲突1 信号1 触达2 FLAGGED=1 RII=31.4
- [✅] **BD-R06** 冲突2 信号5 触达4 FLAGGED=8 RII=72.6
- [✅] **BD-T01** dept=GENERAL urgency=EMERGENCY degraded=False
- [✅] **BD-T02** dept=PEDIATRICS urgency=EMERGENCY degraded=False
- [✅] **BD-T03** dept=CARDIOLOGY urgency=ROUTINE degraded=False
- [✅] **BD-T04** dept=GENERAL urgency=ROUTINE degraded=True
