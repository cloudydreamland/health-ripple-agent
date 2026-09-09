-- 智慧云脑·健康事件涟漪守护智能体 - MySQL Schema
-- 从 kingbase_schema.sql 转换而来，适配 MySQL 8.x

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `smart_cloud_brain` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `smart_cloud_brain`;

CREATE TABLE IF NOT EXISTS patient (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  phone VARCHAR(20) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  gender VARCHAR(10),
  age INT,
  allergy_history VARCHAR(500),
  past_history VARCHAR(1000),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS department (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS doctor (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  phone VARCHAR(20) UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  department_id BIGINT NOT NULL,
  title VARCHAR(50),
  specialty VARCHAR(500),
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS triage_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  chief_complaint TEXT NOT NULL,
  recommended_department VARCHAR(100),
  recommended_doctor_ids VARCHAR(255),
  assigned_doctor_id BIGINT,
  reason TEXT,
  ai_result_json TEXT,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_triage_patient (patient_id),
  INDEX idx_triage_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS doctor_schedule (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  doctor_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  work_date DATE NOT NULL,
  time_range VARCHAR(30) NOT NULL,
  capacity INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_schedule_doctor (doctor_id),
  INDEX idx_schedule_date (work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appointment_slot (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  schedule_id BIGINT,
  doctor_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP NOT NULL,
  capacity INT NOT NULL,
  remaining_capacity INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_slot_doctor (doctor_id),
  INDEX idx_slot_start (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_schedule_suggestion (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  doctor_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  work_date DATE NOT NULL,
  time_range VARCHAR(30) NOT NULL,
  capacity INT NOT NULL,
  reason TEXT,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS registration (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  triage_record_id BIGINT,
  slot_id BIGINT,
  appointment_time TIMESTAMP NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_reg_patient (patient_id),
  INDEX idx_reg_doctor (doctor_id),
  INDEX idx_reg_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS medical_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  registration_id BIGINT NOT NULL UNIQUE,
  chief_complaint TEXT NOT NULL,
  present_illness TEXT,
  past_history TEXT,
  physical_exam TEXT,
  diagnosis TEXT,
  treatment_plan TEXT,
  ai_generated_json TEXT,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_mr_patient (patient_id),
  INDEX idx_mr_doctor (doctor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS prescription (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  registration_id BIGINT,
  medical_record_id BIGINT,
  diagnosis VARCHAR(500),
  drugs_json TEXT,
  ai_check_json TEXT,
  risk_level VARCHAR(20),
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_rx_patient (patient_id),
  INDEX idx_rx_doctor (doctor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS drug (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  generic_name VARCHAR(100),
  category VARCHAR(50),
  specification VARCHAR(100),
  contraindications TEXT,
  interactions TEXT,
  adverse_reactions TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_drug_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_generation_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  service VARCHAR(50) NOT NULL,
  prompt TEXT,
  result TEXT,
  model VARCHAR(100),
  latency_ms INT,
  success TINYINT(1) NOT NULL DEFAULT 1,
  error_message TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ai_log_service (service),
  INDEX idx_ai_log_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notification (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  recipient VARCHAR(100) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  content TEXT,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_notif_recipient (recipient)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS followup_plan (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  diagnosis VARCHAR(500),
  recheck_date DATE,
  medication_reminder TEXT,
  notes TEXT,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_followup_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS followup_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  survey_json TEXT,
  patient_status VARCHAR(20),
  abnormal_flag TINYINT(1) NOT NULL DEFAULT 0,
  submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_fr_plan (plan_id),
  INDEX idx_fr_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS medication_reminder (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT,
  patient_id BIGINT NOT NULL,
  drug_name VARCHAR(100),
  dosage VARCHAR(100),
  frequency VARCHAR(100),
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_reminder_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 健康事件涟漪守护核心创新相关表
CREATE TABLE IF NOT EXISTS drug_lifestyle_conflict (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  drug_keyword VARCHAR(100) NOT NULL,
  conflict_item VARCHAR(100) NOT NULL,
  risk_description TEXT,
  severity VARCHAR(20) NOT NULL,
  advice TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_dlc_drug (drug_keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS complication_signal (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  diagnosis_keyword VARCHAR(100) NOT NULL,
  signal_symptom VARCHAR(255) NOT NULL,
  complication VARCHAR(255),
  action_advice VARCHAR(255),
  urgency VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_cs_diagnosis (diagnosis_keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recheck_window (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  diagnosis_keyword VARCHAR(100) NOT NULL,
  item VARCHAR(255) NOT NULL,
  timing VARCHAR(100) NOT NULL,
  chrono_type VARCHAR(20) NOT NULL,
  advice TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_rw_diagnosis (diagnosis_keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS counterfactual_evidence (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
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
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ce_decision_id (decision_id),
  INDEX idx_ce_type (decision_type),
  INDEX idx_ce_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 涟漪推演事件（一次推演 = 一条记录，五维图谱 JSON 持久化）
CREATE TABLE IF NOT EXISTS ripple_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  diagnosis VARCHAR(255),
  drugs_json TEXT,
  past_history VARCHAR(1000),
  ripple_graph_json TEXT,
  total_nodes INT,
  high_risk_count INT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_re_patient (patient_id),
  INDEX idx_re_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 医疗时间学触达计划（窗口期/节律/周期/季节，DuMate 定时任务轮询到期项）
CREATE TABLE IF NOT EXISTS chrono_trigger (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  ripple_event_id BIGINT,
  chrono_type VARCHAR(20) NOT NULL,
  event VARCHAR(255) NOT NULL,
  trigger_time VARCHAR(100),
  next_trigger_at DATETIME,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  action VARCHAR(255),
  last_fired_at DATETIME,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ct_patient (patient_id),
  INDEX idx_ct_due (status, next_trigger_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 多智能体 MDT 会诊记录（五 Agent 多视角聚合纪要）
CREATE TABLE IF NOT EXISTS mdt_consultation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  mdt_id VARCHAR(50) NOT NULL UNIQUE,
  patient_id BIGINT,
  chief_complaint VARCHAR(500),
  past_history_json TEXT,
  diagnosis VARCHAR(255),
  drugs_json TEXT,
  consultation_json TEXT,
  consensus_json TEXT,
  agent_count INT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_mdt_mdt_id (mdt_id),
  INDEX idx_mdt_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 医疗时间学规则知识库（结构化字段供 ChronoEngine 计算 nextTriggerAt）
CREATE TABLE IF NOT EXISTS chrono_rule (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  diagnosis_keyword VARCHAR(100) NOT NULL,
  chrono_type VARCHAR(20) NOT NULL,
  event VARCHAR(255) NOT NULL,
  trigger_time VARCHAR(100),
  action VARCHAR(255),
  start_hour INT,
  offset_days INT,
  period_days INT,
  target_month INT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_cr_diagnosis (diagnosis_keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子数据：科室
INSERT INTO department (code, name, description) VALUES
  ('internal', '内科', '常见内科疾病'),
  ('cardiology', '心内科', '心血管疾病'),
  ('respiratory', '呼吸内科', '呼吸系统疾病'),
  ('endocrinology', '内分泌科', '糖尿病等内分泌疾病'),
  ('surgery', '外科', '常见外科疾病'),
  ('emergency', '急诊科', '急危重症')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 种子数据：医生（密码哈希使用占位符，实际应使用BCrypt）
INSERT INTO doctor (name, phone, password_hash, department_id, title, specialty, status) VALUES
  ('张医生', '13800000001', '$2a$10$placeholder_hash_1', 1, '主治医师', '内科常见病', 'ACTIVE'),
  ('李医生', '13800000002', '$2a$10$placeholder_hash_2', 2, '副主任医师', '冠心病、高血压', 'ACTIVE'),
  ('王医生', '13800000003', '$2a$10$placeholder_hash_3', 3, '主治医师', '哮喘、慢阻肺', 'ACTIVE'),
  ('赵医生', '13800000004', '$2a$10$placeholder_hash_4', 4, '副主任医师', '糖尿病、甲状腺', 'ACTIVE'),
  ('陈医生', '13800000005', '$2a$10$placeholder_hash_5', 5, '主任医师', '普通外科', 'ACTIVE'),
  ('急诊医生', '13800000006', '$2a$10$placeholder_hash_6', 6, '主治医师', '急危重症', 'ACTIVE')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 种子数据：患者（密码哈希使用占位符）
INSERT INTO patient (name, phone, password_hash, gender, age, allergy_history, past_history) VALUES
  ('测试患者', '13900000001', '$2a$10$placeholder_hash_p1', '男', 65, '青霉素', '糖尿病,高血压,慢性肾病'),
  ('张三', '13900000002', '$2a$10$placeholder_hash_p2', '男', 45, '', '高血压'),
  ('李四', '13900000003', '$2a$10$placeholder_hash_p3', '女', 55, '磺胺', '2型糖尿病')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 种子数据：药品
INSERT INTO drug (name, generic_name, category, specification, contraindications, interactions, adverse_reactions) VALUES
  ('阿莫西林胶囊', '阿莫西林', '抗生素', '0.25g*24粒', '青霉素过敏者禁用', '与丙磺舒合用升高血药浓度；与甲氨蝶呤合用增加毒性', '皮疹、腹泻、过敏反应'),
  ('布洛芬片', '布洛芬', '解热镇痛', '0.2g*12片', '消化道溃疡、严重肝肾功能不全者禁用', '与华法林合用增加出血风险；与ACEI合用降低降压效果', '胃肠道反应、消化道出血'),
  ('二甲双胍片', '二甲双胍', '降糖药', '0.5g*20片', '肾功能不全、乳酸酸中毒病史禁用', '与造影剂联用需提前停药；与酒精合用增加乳酸酸中毒风险', '胃肠道反应、维生素B12缺乏'),
  ('阿司匹林肠溶片', '阿司匹林', '抗血小板', '100mg*30片', '消化道溃疡、血友病禁用', '与华法林合用增加出血风险；与布洛芬合用降低抗血小板效果', '消化道出血、过敏反应'),
  ('硝苯地平控释片', '硝苯地平', '降压药', '30mg*7片', '心源性休克禁用', '与利福平合用降低药效；与葡萄柚汁合用升高血药浓度', '头痛、面部潮红、下肢水肿')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 种子数据：药物-生活冲突知识库（涟漪守护核心创新）
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
  ('甲氨蝶呤', '饮酒', '肝损伤加重', 'HIGH', '服药期间禁止饮酒')
ON DUPLICATE KEY UPDATE risk_description=VALUES(risk_description);

-- 种子数据：并发症早期信号（涟漪守护核心创新）
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
  ('慢性肾病', '呼吸困难/不能平卧', '心衰/容量超负荷', '立即急诊', 'HIGH')
ON DUPLICATE KEY UPDATE action_advice=VALUES(action_advice);

-- 种子数据：复查窗口规则（医疗时间学·周期性）
INSERT INTO recheck_window (diagnosis_keyword, item, timing, chrono_type, advice) VALUES
  ('2型糖尿病', '肝肾功能+空腹血糖', '服药2周后', 'PERIODIC', '二甲双胍起始治疗后必查'),
  ('2型糖尿病', '糖化血红蛋白(HbA1c)', '3个月后', 'PERIODIC', '评估血糖长期控制'),
  ('2型糖尿病', '眼底/足部/尿微量白蛋白', '每年', 'PERIODIC', '并发症筛查'),
  ('高血压', '血压复查', '服药2周后', 'PERIODIC', '评估降压效果'),
  ('高血压', '肝肾功能+电解质', '1-3个月后', 'PERIODIC', 'ACEI/利尿剂监测'),
  ('高血压', '心电图/心脏超声', '每年', 'PERIODIC', '靶器官损害评估'),
  ('冠心病', '症状复查+心电图', '服药1-2周后', 'PERIODIC', '评估治疗反应'),
  ('华法林', 'INR凝血指标', '服药3-5天后', 'PERIODIC', '调整剂量必查'),
  ('华法林', 'INR凝血指标', '稳定后每月', 'PERIODIC', '稳定期监测')
ON DUPLICATE KEY UPDATE advice=VALUES(advice);

-- 种子数据：医疗时间学规则库（窗口期/节律/周期/季节，ChronoEngine 结构化计算）
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

SET FOREIGN_KEY_CHECKS = 1;
