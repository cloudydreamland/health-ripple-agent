-- followup-service 数据表（PostgreSQL/KingbaseES）
-- 创建在 smart_cloud_brain 数据库中

CREATE TABLE IF NOT EXISTS followup_plan (
  id BIGSERIAL PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  diagnosis VARCHAR(255),
  medications TEXT,
  followup_days INTEGER DEFAULT 7,
  followup_date VARCHAR(20),
  reminder_schedule TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_followup_plan_patient ON followup_plan(patient_id);

CREATE TABLE IF NOT EXISTS followup_record (
  id BIGSERIAL PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  status VARCHAR(50) NOT NULL,
  notes TEXT,
  abnormal BOOLEAN DEFAULT FALSE,
  doctor_notified BOOLEAN DEFAULT FALSE,
  submitted_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_followup_record_plan FOREIGN KEY (plan_id) REFERENCES followup_plan(id)
);

CREATE INDEX IF NOT EXISTS idx_followup_record_plan ON followup_record(plan_id);
