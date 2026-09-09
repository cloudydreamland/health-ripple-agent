-- ripple-service 数据表（PostgreSQL/KingbaseES / MySQL 兼容核心字段）
-- 创建在 smart_cloud_brain 数据库中
-- 涟漪守护核心创新：五维图谱 / 反事实证据链 / 时间学引擎 / MDT 会诊

-- ============ 知识库（规则表，种子数据随建表预置） ============

CREATE TABLE IF NOT EXISTS drug_lifestyle_conflict (
  id BIGSERIAL PRIMARY KEY,
  drug_keyword VARCHAR(100) NOT NULL,
  conflict_item VARCHAR(100) NOT NULL,
  risk_description TEXT,
  severity VARCHAR(20) NOT NULL,
  advice TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dlc_drug ON drug_lifestyle_conflict(drug_keyword);

CREATE TABLE IF NOT EXISTS complication_signal (
  id BIGSERIAL PRIMARY KEY,
  diagnosis_keyword VARCHAR(100) NOT NULL,
  signal_symptom VARCHAR(255) NOT NULL,
  complication VARCHAR(255),
  action_advice VARCHAR(255),
  urgency VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cs_diagnosis ON complication_signal(diagnosis_keyword);

CREATE TABLE IF NOT EXISTS recheck_window (
  id BIGSERIAL PRIMARY KEY,
  diagnosis_keyword VARCHAR(100) NOT NULL,
  item VARCHAR(255) NOT NULL,
  timing VARCHAR(100) NOT NULL,
  chrono_type VARCHAR(20) NOT NULL,
  advice TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rw_diagnosis ON recheck_window(diagnosis_keyword);

-- 医疗时间学规则（结构化字段供 ChronoEngine 计算 nextTriggerAt）
CREATE TABLE IF NOT EXISTS chrono_rule (
  id BIGSERIAL PRIMARY KEY,
  diagnosis_keyword VARCHAR(100) NOT NULL,
  chrono_type VARCHAR(20) NOT NULL,
  event VARCHAR(255) NOT NULL,
  trigger_time VARCHAR(100),
  action VARCHAR(255),
  start_hour INT,
  offset_days INT,
  period_days INT,
  target_month INT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cr_diagnosis ON chrono_rule(diagnosis_keyword);

-- ============ 运行时表 ============

-- 涟漪推演事件（一次推演 = 一条记录，五维图谱 JSON 持久化）
CREATE TABLE IF NOT EXISTS ripple_event (
  id BIGSERIAL PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  diagnosis VARCHAR(255),
  drugs_json TEXT,
  past_history VARCHAR(1000),
  ripple_graph_json TEXT,
  total_nodes INT,
  high_risk_count INT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_re_patient ON ripple_event(patient_id);

-- 反事实决策证据链（tamper-evident hash chain，区块链式防篡改）
CREATE TABLE IF NOT EXISTS counterfactual_evidence (
  id BIGSERIAL PRIMARY KEY,
  decision_id VARCHAR(100) NOT NULL UNIQUE,
  decision_type VARCHAR(50) NOT NULL,
  patient_id BIGINT,
  chosen_path TEXT,
  alternative_paths_json TEXT,
  inputs_json TEXT,
  considered_factors_json TEXT,
  decision_json TEXT,
  confidence DECIMAL(5,4),
  action_taken VARCHAR(100),
  agent_id VARCHAR(100),
  hash VARCHAR(64),
  prev_hash VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ce_type ON counterfactual_evidence(decision_type);
CREATE INDEX IF NOT EXISTS idx_ce_patient ON counterfactual_evidence(patient_id);

-- 医疗时间学触达计划（DuMate 定时任务轮询到期项）
CREATE TABLE IF NOT EXISTS chrono_trigger (
  id BIGSERIAL PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  ripple_event_id BIGINT,
  chrono_type VARCHAR(20) NOT NULL,
  event VARCHAR(255) NOT NULL,
  trigger_time VARCHAR(100),
  next_trigger_at TIMESTAMP,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  action VARCHAR(255),
  last_fired_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ct_patient ON chrono_trigger(patient_id);
CREATE INDEX IF NOT EXISTS idx_ct_due ON chrono_trigger(status, next_trigger_at);

-- 多智能体 MDT 会诊记录（五 Agent 多视角聚合纪要）
CREATE TABLE IF NOT EXISTS mdt_consultation (
  id BIGSERIAL PRIMARY KEY,
  mdt_id VARCHAR(50) NOT NULL UNIQUE,
  patient_id BIGINT,
  chief_complaint VARCHAR(500),
  past_history_json TEXT,
  diagnosis VARCHAR(255),
  drugs_json TEXT,
  consultation_json TEXT,
  consensus_json TEXT,
  agent_count INT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_mdt_patient ON mdt_consultation(patient_id);

-- ============ 种子数据（与服务端 KnowledgeBaseSeeder 幂等对齐） ============

INSERT INTO drug_lifestyle_conflict (drug_keyword, conflict_item, risk_description, severity, advice) VALUES
  ('二甲双胍', '饮酒', '乳酸酸中毒（严重可致死）', 'HIGH', '服药期间禁止饮酒'),
  ('二甲双胍', '维生素B12缺乏', '长期服用致B12缺乏性贫血', 'MEDIUM', '建议定期监测B12水平'),
  ('二甲双胍', '造影剂联用', '肾损伤', 'HIGH', '造影检查前需提前停药48小时'),
  ('华法林', '柚子/葡萄柚', '增强抗凝效果，出血风险', 'HIGH', '服药期间禁止食用柚子'),
  ('华法林', '大量绿叶蔬菜', '降低抗凝效果，血栓风险', 'MEDIUM', '保持稳定摄入量，勿突然增减'),
  ('阿莫西林', '饮酒', '双硫仑样反应', 'MEDIUM', '服药期间及停药后7天避免饮酒'),
  ('布洛芬', '饮酒', '胃肠道出血', 'HIGH', '服药期间禁止饮酒'),
  ('布洛芬', '空腹服用', '胃黏膜损伤', 'MEDIUM', '建议餐后服用'),
  ('他汀类', '柚子/葡萄柚', '肌病/横纹肌溶解风险', 'HIGH', '服药期间禁止食用柚子'),
  ('头孢类', '饮酒', '双硫仑样反应（严重可致死）', 'HIGH', '服药期间及停药后7-10天禁止饮酒'),
  ('磺脲类降糖药', '饮酒', '低血糖/双硫仑反应', 'HIGH', '服药期间禁止饮酒'),
  ('四环素', '日晒', '光敏反应', 'MEDIUM', '服药期间避免强烈日晒'),
  ('甲氨蝶呤', '饮酒', '肝损伤加重', 'HIGH', '服药期间禁止饮酒');

INSERT INTO complication_signal (diagnosis_keyword, signal_symptom, complication, action_advice, urgency) VALUES
  ('2型糖尿病', '视力模糊/飞蚊症突发', '糖尿病视网膜病变', '立即眼科就诊', 'HIGH'),
  ('2型糖尿病', '足部感觉异常/伤口不愈', '糖尿病足', '立即外科就诊', 'HIGH'),
  ('2型糖尿病', '心悸/出汗/手抖/饥饿感', '低血糖', '即时补糖并就医', 'HIGH'),
  ('2型糖尿病', '多尿/多饮/乏力加重/意识模糊', '糖尿病酮症酸中毒', '立即急诊', 'HIGH'),
  ('高血压', '剧烈头痛/呕吐/视物模糊', '高血压危象', '立即急诊', 'HIGH'),
  ('高血压', '胸痛/胸闷/大汗', '心肌梗死/主动脉夹层', '立即急诊（黄金120分钟）', 'HIGH'),
  ('高血压', '肢体麻木/言语不清/面瘫', '脑卒中', '立即急诊（黄金3小时）', 'HIGH'),
  ('冠心病', '持续胸痛>15分钟/含服硝酸甘油不缓解', '急性心肌梗死', '立即急诊（黄金120分钟）', 'HIGH'),
  ('冠心病', '夜间阵发呼吸困难/不能平卧', '心力衰竭', '心内科就诊', 'HIGH'),
  ('哮喘', '呼吸困难加重/讲话困难/嗜睡', '哮喘持续状态', '立即急诊', 'HIGH'),
  ('慢性肾病', '尿量骤减/水肿加重', '肾功能急性恶化', '立即肾内科', 'HIGH'),
  ('慢性肾病', '呼吸困难/不能平卧', '心衰/容量超负荷', '立即急诊', 'HIGH');

INSERT INTO recheck_window (diagnosis_keyword, item, timing, chrono_type, advice) VALUES
  ('2型糖尿病', '肝肾功能+空腹血糖', '服药2周后', 'PERIODIC', '二甲双胍起始治疗后必查'),
  ('2型糖尿病', '糖化血红蛋白(HbA1c)', '3个月后', 'PERIODIC', '评估血糖长期控制'),
  ('2型糖尿病', '眼底/足部/尿微量白蛋白', '每年', 'PERIODIC', '并发症筛查'),
  ('高血压', '血压复查', '服药2周后', 'PERIODIC', '评估降压效果'),
  ('高血压', '肝肾功能+电解质', '1-3个月后', 'PERIODIC', 'ACEI/利尿剂监测'),
  ('高血压', '心电图/心脏超声', '每年', 'PERIODIC', '靶器官损害评估'),
  ('冠心病', '症状复查+心电图', '服药1-2周后', 'PERIODIC', '评估治疗反应'),
  ('华法林', 'INR凝血指标', '服药3-5天后', 'PERIODIC', '调整剂量必查'),
  ('华法林', 'INR凝血指标', '稳定后每月', 'PERIODIC', '稳定期监测');

INSERT INTO chrono_rule (diagnosis_keyword, chrono_type, event, trigger_time, action, start_hour, offset_days, period_days, target_month) VALUES
  ('冠心病', 'WINDOW', '心梗黄金救治窗口', '胸痛发作后120分钟内', '即时高优先级触达并引导急诊', NULL, NULL, NULL, NULL),
  ('2型糖尿病', 'RHYTHM', '夜间0-3点低血糖高发', '凌晨0-3点', '主动询问患者状态', 0, NULL, NULL, NULL),
  ('高血压', 'RHYTHM', '凌晨血压晨峰', '凌晨4-6点', '主动询问晨起血压', 4, NULL, NULL, NULL),
  ('哮喘', 'RHYTHM', '夜间哮喘发作高峰', '凌晨3-5点', '主动询问呼吸状态', 3, NULL, NULL, NULL),
  ('2型糖尿病', 'PERIODIC', '服药2周后复查肝肾功能', '服药后第14天', '主动提醒复查', NULL, 14, NULL, NULL),
  ('2型糖尿病', 'PERIODIC', '3个月后查糖化血红蛋白', '诊断后第90天', '主动提醒复查', NULL, 90, NULL, NULL),
  ('高血压', 'PERIODIC', '服药2周后血压复查', '服药后第14天', '主动提醒复查', NULL, 14, NULL, NULL),
  ('华法林', 'PERIODIC', '每月复查INR', '稳定后每月', '主动提醒复查INR', NULL, 3, 30, NULL),
  ('2型糖尿病', 'SEASONAL', '换季血糖波动', '秋冬换季', '主动提醒血糖监测', NULL, NULL, NULL, 9),
  ('高血压', 'SEASONAL', '秋冬血压升高', '入秋/入冬', '主动提醒增加监测频次', NULL, NULL, NULL, 9),
  ('哮喘', 'SEASONAL', '春季花粉诱发', '春季花粉季', '主动提醒预防用药', NULL, NULL, NULL, 3);
