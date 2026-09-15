#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
智慧云脑·患者就医全流程端到端测试（E2E）

覆盖完整闭环（模拟真实患者旅程，经网关鉴权调用 12 个微服务）：
  患者注册/登录 → AI分诊 → 预约挂号 → 医生接诊写病历 → AI处方安全审核 → 开处方
  → 随访计划 → 健康事件涟漪推演（五维图谱+反事实树+时间学触达+哈希链证据）
  → 多智能体MDT会诊 → 证据链完整性校验 → 时间学到期触达 → 医生通知（事件驱动）

用法：
    python scripts/e2e_test.py [--base-url http://localhost:18080]

前置条件：deploy/docker-compose.yml 全栈已启动（网关默认 18080）。
退出码：0=全部通过；1=存在失败步骤。
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

# ============================================================
# 结果收集
# ============================================================
RESULTS = []


def record(step, ok, detail, duration_ms):
    RESULTS.append({
        "step": step,
        "ok": bool(ok),
        "detail": detail,
        "durationMs": round(duration_ms),
    })
    mark = "PASS" if ok else "FAIL"
    print(f"[{mark}] {step} ({duration_ms:.0f}ms) {detail}")


class E2EClient:
    """经网关的 HTTP 客户端（Result 包装解析 + Bearer 鉴权）。"""

    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        self.token = None

    def auth(self, token):
        self.token = token

    def call(self, method, path, payload=None, raw=False):
        url = self.base_url + path
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        req.add_header("Accept", "application/json")
        if self.token:
            req.add_header("Authorization", "Bearer " + self.token)
        started = time.time()
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                body = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            try:
                body = json.loads(e.read().decode("utf-8"))
            except Exception:
                body = {"code": e.code, "message": "HTTP " + str(e.code)}
        duration = (time.time() - started) * 1000
        # raw 模式：actuator 等端点返回裸 JSON（无 Result 包装），HTTP 200 即成功
        if raw:
            if not isinstance(body, dict) or body.get("status") not in ("UP",):
                raise AssertionError(f"{method} {path} -> 健康检查失败: {json.dumps(body, ensure_ascii=False)[:300]}")
            return body, duration
        # 后端 Result 约定：code=0（ErrorCode.SUCCESS）为成功；兼容 200
        if not isinstance(body, dict) or body.get("code") not in (0, 200):
            raise AssertionError(f"{method} {path} -> 业务失败: {json.dumps(body, ensure_ascii=False)[:300]}")
        return body.get("data"), duration


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:18080")
    args = parser.parse_args()
    api = E2EClient(args.base_url)

    # ---------- 0. 网关健康 ----------
    try:
        data, ms = api.call("GET", "/actuator/health", raw=True)
        record("0.网关健康检查", data is not None, f"status={data.get('status')}", ms)
    except AssertionError as e:
        record("0.网关健康检查", False, str(e), 0)
        return summary()

    # ---------- 1. 患者注册（带丰富既往史，供涟漪推演） ----------
    phone = "139{:08d}".format(int(time.time()) % 100000000)
    try:
        data, ms = api.call("POST", "/api/patient/register", {
            "name": "E2E测试患者",
            "phone": phone,
            "password": "e2e_test_123",
            "gender": "MALE",
            "age": 58,
            "allergyHistory": "无",
            "pastHistory": "2型糖尿病,高血压",
        })
        patient_id = data.get("patientId")
        assert patient_id, f"响应缺 patientId: {data}"
        record("1.患者注册", True, f"patientId={patient_id}", ms)
    except AssertionError as e:
        record("1.患者注册", False, str(e), 0)
        return summary()

    # ---------- 2. 患者登录 ----------
    try:
        data, ms = api.call("POST", "/api/patient/login", {"account": phone, "password": "e2e_test_123"})
        patient_token = data.get("token")
        assert patient_token and data.get("role") == "PATIENT", f"登录响应异常: {data}"
        api.auth(patient_token)
        record("2.患者登录", True, f"role={data.get('role')}, name={data.get('name')}", ms)
    except AssertionError as e:
        record("2.患者登录", False, str(e), 0)
        return summary()

    # ---------- 3. AI 分诊 ----------
    triage = {}
    try:
        data, ms = api.call("POST", "/api/triage/consult", {
            "patientId": patient_id,
            "chiefComplaint": "多饮多尿伴血糖升高3个月，近期胸闷气短",
            "symptoms": "口渴,多尿,乏力,活动后胸闷",
            "age": 58,
            "gender": "MALE",
            "allergyHistory": "无",
            "pastHistory": "2型糖尿病,高血压",
        })
        triage = data if isinstance(data, dict) else {}
        dept = triage.get("recommendedDepartment") or triage.get("departmentCode")
        assert dept, f"分诊响应缺推荐科室: {json.dumps(triage, ensure_ascii=False)[:200]}"
        assert triage.get("degraded") is False, f"分诊走了降级路径（规则引擎应识别多饮多尿+胸闷）: {triage}"
        record("3.AI分诊", True,
               f"推荐科室={dept}, 紧急度={triage.get('urgencyLevel')}, 置信度={triage.get('confidence')}, 降级={triage.get('degraded')}", ms)
    except AssertionError as e:
        record("3.AI分诊", False, str(e), 0)

    # ---------- 4. 预约挂号（患者，按分诊推荐医生联动） ----------
    registration_id = None
    try:
        # 分诊→挂号闭环：使用分诊推荐的医生（规则引擎推荐心内科 doctorId=1 张医生）
        rec_doctor = 1
        rec_dept = 1
        rec_ids = triage.get("recommendedDoctorIds") or []
        if rec_ids:
            rec_doctor = int(rec_ids[0])
            # 种子数据医生→科室映射：1→1心内科, 2→2全科, 3→3呼吸内科
            rec_dept = {1: 1, 2: 2, 3: 3}.get(rec_doctor, 1)
        tomorrow = (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%d")
        data, ms = api.call("POST", "/api/registration/create", {
            "doctorId": rec_doctor,
            "departmentId": rec_dept,
            "appointmentTime": tomorrow + "T14:30:00",
        })
        registration_id = data.get("registrationId")
        assert registration_id, f"挂号响应缺 registrationId: {data}"
        record("4.预约挂号", True,
               f"registrationId={registration_id}, 按分诊推荐挂 doctorId={rec_doctor}(dept={rec_dept}), 就诊={tomorrow} 14:30", ms)
    except AssertionError as e:
        record("4.预约挂号", False, str(e), 0)

    # ---------- 5. 医生登录 ----------
    medical_record_id = None
    try:
        data, ms = api.call("POST", "/api/doctor/login", {"account": "doctor1", "password": "123456"})
        doctor_token = data.get("token")
        assert doctor_token and data.get("role") == "DOCTOR", f"医生登录响应异常: {data}"
        api.auth(doctor_token)
        record("5.医生登录", True, f"doctor={data.get('name')}(心内科), role=DOCTOR", ms)
    except AssertionError as e:
        record("5.医生登录", False, str(e), 0)
        return summary()

    # ---------- 6. 医生接诊写病历 ----------
    if registration_id:
        try:
            data, ms = api.call("POST", "/api/medical-record/save", {
                "registrationId": registration_id,
                "chiefComplaint": "多饮多尿伴血糖升高3个月，近期胸闷气短",
                "presentIllness": "患者3个月前无明显诱因出现口渴多饮、尿量增多，伴乏力，查空腹血糖11.2mmol/L，"
                                  "近1周活动后胸闷气短，休息后缓解",
                "pastHistory": "2型糖尿病,高血压",
                "physicalExam": "BP 152/94mmHg，心率88次/分，双肺呼吸音清，心界不大，双下肢无水肿",
                "diagnosis": "2型糖尿病;高血压2级",
                "treatmentAdvice": "二甲双胍起始治疗，低盐低脂糖尿病饮食，2周后复查肝肾功能+空腹血糖",
                "aiGenerated": False,
            })
            medical_record_id = data.get("medicalRecordId")
            assert medical_record_id, f"病历响应缺 medicalRecordId: {data}"
            record("6.医生书写病历", True, f"medicalRecordId={medical_record_id}, 诊断={data.get('diagnosis')}", ms)
        except AssertionError as e:
            record("6.医生书写病历", False, str(e), 0)

    # ---------- 7. AI 处方安全审核 ----------
    drugs = [{
        "drugName": "二甲双胍片",
        "dosage": "0.5g",
        "frequency": "每日2次",
        "usageMethod": "口服",
        "days": 30,
        "remark": "随餐服用",
    }]
    check_result = {}
    try:
        data, ms = api.call("POST", "/api/prescription/check", {
            "patientId": patient_id,
            "doctorId": 1,
            "medicalRecordId": medical_record_id,
            "diagnosis": "2型糖尿病;高血压2级",
            "patientAge": 58,
            "patientGender": "MALE",
            "allergyHistory": "无",
            "pastHistory": "2型糖尿病,高血压",
            "drugs": drugs,
        })
        check_result = data if isinstance(data, dict) else {}
        assert "riskLevel" in check_result, f"审核响应缺 riskLevel: {json.dumps(check_result, ensure_ascii=False)[:200]}"
        record("7.AI处方安全审核", True,
               f"riskLevel={check_result.get('riskLevel')}, 降级={check_result.get('degraded')}", ms)
    except AssertionError as e:
        record("7.AI处方安全审核", False, str(e), 0)

    # ---------- 8. 开具处方 ----------
    prescription_id = None
    try:
        data, ms = api.call("POST", "/api/prescription/create", {
            "patientId": patient_id,
            "medicalRecordId": medical_record_id,
            "riskLevel": check_result.get("riskLevel") or "LOW",
            "drugs": drugs,
        })
        prescription_id = data.get("prescriptionId")
        assert prescription_id, f"处方响应缺 prescriptionId: {data}"
        record("8.开具处方", True, f"prescriptionId={prescription_id}, 药品=二甲双胍片", ms)
    except AssertionError as e:
        record("8.开具处方", False, str(e), 0)

    # ---------- 9. 制定随访计划 ----------
    try:
        data, ms = api.call("POST", "/api/followup/create", {
            "patientId": patient_id,
            "diagnosis": "2型糖尿病;高血压2级",
            "medications": "二甲双胍片 0.5g 每日2次",
            "followupDays": 14,
            "followupDate": (datetime.now() + timedelta(days=14)).strftime("%Y-%m-%d"),
            "reminderSchedule": "第3天:血糖自测情况;第7天:症状变化;第14天:复查提醒",
        })
        plan_id = data.get("planId") or data.get("id")
        record("9.随访计划制定", plan_id is not None, f"planId={plan_id}, 周期=14天", ms)
    except AssertionError as e:
        record("9.随访计划制定", False, str(e), 0)

    # ---------- 10. 健康事件涟漪推演（核心创新） ----------
    decision_id = None
    ripple = {}
    try:
        data, ms = api.call("POST", "/api/health-event/ripple", {
            "diagnosis": "2型糖尿病",
            "drugs": [{"drugName": "二甲双胍"}],
            "patientId": patient_id,
            "pastHistory": "高血压",
        })
        ripple = data if isinstance(data, dict) else {}
        dims = data.get("dimensions") or {}
        dim_count = len(dims)
        cft = data.get("counterfactualTree") or {}
        triggers = data.get("chronoTriggers") or []
        evidence = data.get("evidenceChain") or {}
        decision_id = evidence.get("decisionId")
        assert dim_count == 5, f"五维图谱实际维度={dim_count}"
        assert cft.get("counterfactualCount", 0) >= 3, f"反事实树路径数={cft.get('counterfactualCount')}"
        assert len(triggers) >= 4, f"时间学触达数={len(triggers)}"
        assert decision_id, "证据链缺 decisionId"
        # 反事实护栏：审计结论存在且FLAGGED路径禁止下发
        guardrail = cft.get("guardrailSummary") or {}
        assert guardrail.get("auditedPaths", 0) >= 3, f"护栏未审计反事实路径: {guardrail}"
        assert guardrail.get("flaggedPaths", 0) >= 1, f"高危场景应有FLAGGED路径: {guardrail}"
        record("10.涟漪推演", True,
               f"五维图谱={dim_count}维, 反事实路径={cft.get('counterfactualCount')}条"
               f"(护栏审计={guardrail.get('auditedPaths')}/FLAGGED={guardrail.get('flaggedPaths')}), "
               f"时间学触达={len(triggers)}项, evidence={str(decision_id)[:40]}", ms)
    except AssertionError as e:
        record("10.涟漪推演", False, str(e), 0)

    # ---------- 10b. 涟漪强度指数 RII（量化模型：把"涟漪"从比喻升级为可计算模型） ----------
    if ripple:
        try:
            rii = ripple.get("rippleIntensity") or {}
            index = rii.get("index")
            assert isinstance(index, (int, float)) and 0 < index <= 100, f"RII指数异常: {rii}"
            assert rii.get("level") in ("RED", "ORANGE", "YELLOW"), f"RII等级缺失: {rii}"
            radius = rii.get("radius")
            assert isinstance(radius, int) and radius >= 1, f"有效扩散半径异常: {rii}"
            top = rii.get("topRisks") or []
            assert 1 <= len(top) <= 3, f"Top风险数异常: {len(top)}"
            # 节点级：每个涟漪节点携带环数/强度/评分依据（评分过程可审计）
            dims_rii = ripple.get("dimensions") or {}
            node_count = 0
            for _name, _nodes in dims_rii.items():
                for _n in _nodes:
                    assert "intensity" in _n and "ring" in _n and "scoreBreakdown" in _n, \
                        f"{_name} 节点缺强度标注: {json.dumps(_n, ensure_ascii=False)[:120]}"
                    node_count += 1
            record("10b.涟漪强度指数RII", True,
                   f"RII={index}({rii.get('levelLabel')}), 有效扩散半径={radius}环, "
                   f"Top风险={[t.get('label') for t in top][:2]}, {node_count}节点全携带强度标注", 0)
        except AssertionError as e:
            record("10b.涟漪强度指数RII", False, str(e), 0)

    # ---------- 11. 多智能体 MDT 会诊 ----------
    try:
        data, ms = api.call("POST", "/api/mdt/consult", {
            "patientId": patient_id,
            "chiefComplaint": "胸闷气短3天",
            "pastHistory": "2型糖尿病,高血压,慢性肾病",
            "diagnosis": "冠心病待排",
            "drugs": [{"drugName": "二甲双胍"}],
        })
        consultation = data.get("consultation") or {}
        consensus_notes = data.get("consensusNotes") or []
        agent_count = len(consultation)
        assert agent_count == 5, f"MDT视角数={agent_count}"
        assert isinstance(consensus_notes, list) and len(consensus_notes) >= 2, f"MDT共识要点数={len(consensus_notes)}"
        mdt_evidence = (data.get("evidenceChain") or {}).get("decisionId")
        record("11.MDT多智能体会诊", True,
               f"五Agent={agent_count}视角, 共识要点={len(consensus_notes)}条, "
               f"evidence={str(mdt_evidence)[:40]}", ms)
    except AssertionError as e:
        record("11.MDT多智能体会诊", False, str(e), 0)

    # ---------- 11b. MDT结论→冠心病涟漪推演（WINDOW黄金窗口触达） ----------
    try:
        data, ms = api.call("POST", "/api/health-event/ripple", {
            "diagnosis": "冠心病",
            "drugs": [],
            "patientId": patient_id,
            "pastHistory": "2型糖尿病,高血压",
        })
        mdt_triggers = data.get("chronoTriggers") or []
        window = [t for t in mdt_triggers if t.get("chronoType") == "WINDOW"]
        assert window, f"冠心病推演未生成WINDOW窗口期触达: {json.dumps(mdt_triggers, ensure_ascii=False)[:200]}"
        assert window[0].get("triggerId"), "WINDOW触达缺 triggerId"
        # Timing Card 循证卡片：WINDOW触达必须携带可审计医学依据
        card = window[0].get("timingCard") or {}
        assert card.get("evidenceBasis"), f"WINDOW触达缺Timing Card循证依据: {card}"
        assert card.get("missCost"), f"WINDOW触达缺Timing Card错过代价: {card}"
        assert card.get("evidenceLevel") == "GUIDELINE", f"心梗窗口应为指南级证据: {card}"
        record("11b.冠心病窗口期涟漪推演", True,
               f"WINDOW触达={len(window)}项(事件={window[0].get('event')}), "
               f"TimingCard[依据={str(card.get('evidenceBasis'))[:30]}..., "
               f"证据级={card.get('evidenceLevel')}], 冠心病总触达={len(mdt_triggers)}项", ms)
    except AssertionError as e:
        record("11b.冠心病窗口期涟漪推演", False, str(e), 0)

    # ---------- 12. 证据链完整性校验（tamper-evident） ----------
    try:
        data, ms = api.call("GET", "/api/evidence/verify")
        assert data.get("valid") is True, f"证据链校验失败: {data}"
        record("12.证据链完整性校验", True, f"valid=true, count={data.get('count')}条决策记录", ms)
    except AssertionError as e:
        record("12.证据链完整性校验", False, str(e), 0)

    # ---------- 13. 单条证据可追溯（反事实树可追问） ----------
    if decision_id:
        try:
            data, ms = api.call("GET", "/api/evidence/" + decision_id)
            alt_paths = data.get("alternativePaths") or []
            assert data.get("chosenPath"), "证据缺 chosenPath"
            record("13.证据可追溯查询", True,
                   f"decisionType={data.get('decisionType')}, 反事实路径={len(alt_paths)}条, hash前8位={str(data.get('hash'))[:8]}", ms)
        except AssertionError as e:
            record("13.证据可追溯查询", False, str(e), 0)

    # ---------- 13b. FHIR Provenance 标准导出（HL7 AI Transparency 对齐） ----------
    if decision_id:
        try:
            data, ms = api.call("GET", "/api/evidence/" + decision_id + "/fhir")
            assert data.get("resourceType") == "Provenance", f"非FHIR Provenance资源: {data.get('resourceType')}"
            agent = (data.get("agent") or [{}])[0]
            sig = (data.get("signature") or [{}])[0]
            assert agent.get("who", {}).get("display"), "FHIR agent缺AI参与者标识"
            assert sig.get("data"), "FHIR signature缺哈希链签名"
            entity = (data.get("entity") or [{}])[0]
            details = entity.get("detail") or []
            assert len(details) >= 3, f"FHIR entity决策依据不足: {len(details)}项"
            record("13b.FHIR Provenance导出", True,
                   f"resourceType=Provenance, agent={agent.get('who', {}).get('display')}, "
                   f"entity依据={len(details)}项, signature哈希前8位={str(sig.get('data'))[:8]}", ms)
        except AssertionError as e:
            record("13b.FHIR Provenance导出", False, str(e), 0)

    # ---------- 14. 时间学触达（到期查询 + ack 推进） ----------
    try:
        data, ms = api.call("GET", "/api/chrono/triggers/patient/" + str(patient_id))
        triggers = data if isinstance(data, list) else data.get("triggers", [])
        assert len(triggers) >= 4, f"患者时间学计划数={len(triggers)}"
        types = sorted({t.get("chronoType") for t in triggers})
        missing = {"WINDOW", "RHYTHM", "PERIODIC", "SEASONAL"} - set(types)
        assert not missing, f"患者时间学计划缺关键类型: {missing}, 实际={types}"
        record("14a.时间学触达计划", True,
               f"计划数={len(triggers)}, 四类齐全={types}", ms)
        # ack 一条 RHYTHM 触达验证推进
        rhythm = next((t for t in triggers if t.get("chronoType") == "RHYTHM"), None)
        if rhythm:
            tid = rhythm.get("triggerId") or rhythm.get("id")
            data, ms = api.call("POST", "/api/chrono/trigger/" + str(tid) + "/ack")
            record("14b.时间学ack推进", data.get("status") == "ACTIVE",
                   f"triggerId={tid}, nextTriggerAt={data.get('nextTriggerAt')}", ms)
        else:
            record("14b.时间学ack推进", False, "无RHYTHM触达可ack", 0)
    except AssertionError as e:
        record("14a.时间学触达计划", False, str(e), 0)

    # ---------- 14c. 干预回执 → 涟漪消解闭环 ----------
    try:
        data, ms = api.call("GET", "/api/chrono/triggers/patient/" + str(patient_id))
        triggers_all = data if isinstance(data, list) else data.get("triggers", [])
        target = next((t for t in triggers_all
                       if t.get("chronoType") == "WINDOW" and t.get("status") in ("ACTIVE", "FIRED")), None)
        if target is None:
            target = next((t for t in triggers_all if t.get("status") == "ACTIVE"), None)
        assert target, f"无可回执触达: {len(triggers_all)}条"
        tid = target.get("triggerId") or target.get("id")
        from urllib.parse import quote as _q
        api.call("POST", "/api/chrono/trigger/" + str(tid) + "/feedback?outcome=RESOLVED&note=" + _q("已按提醒完成复查，指标正常"))
        data2, _ = api.call("GET", "/api/health-event/ripple/resolution?patientId=" + str(patient_id))
        assert int(data2.get("resolvedCount", 0)) >= 1, f"RESOLVED回执未计数: {data2}"
        assert float(data2.get("totalIntensity", 0)) > 0, f"触达缺RII强度权重: {data2}"
        assert float(data2.get("resolutionRate", 0)) > 0, f"消解率应>0: {data2}"
        ledger, _ = api.call("GET", "/api/health-event/ripple/feedback-ledger?patientId=" + str(patient_id))
        ledger_list = ledger if isinstance(ledger, list) else []
        assert ledger_list and all("timingCard" in item and "intensity" in item for item in ledger_list), \
            "回执明细缺timingCard/intensity字段"
        record("14c.干预回执消解闭环", True,
               f"RESOLVED回执 → resolvedCount={data2.get('resolvedCount')}, 消解率={data2.get('resolutionRate')}%, "
               f"强度和={data2.get('totalIntensity')}, 明细{len(ledger_list)}条", ms)
    except AssertionError as e:
        record("14c.干预回执消解闭环", False, str(e), 0)

    # ---------- 14d. 健康气象日报 ----------
    try:
        weather = {}
        for _ in range(3):
            api.call("POST", "/api/health-event/ripple", {
                "diagnosis": "冠心病",
                "drugs": [],
                "patientId": patient_id,
                "pastHistory": "2型糖尿病,高血压",
            })
            data, ms = api.call("GET", "/api/health-weather/daily?patientId=" + str(patient_id))
            if int(data.get("dueTodayCount", 0)) >= 1:
                weather = data
                break
        if not weather:
            weather = data
        assert weather.get("weather") in ("SUNNY", "CLOUDY", "RAIN", "STORM"), f"气象等级非法: {weather}"
        assert 0 <= float(weather.get("index", -1)) <= 100, f"气象指数越界: {weather}"
        items = weather.get("items") or []
        assert items, f"今日事项不应为空: {weather.get('weather')}"
        assert items[0].get("timingCard", {}).get("evidenceBasis"), f"今日事项应携带Timing Card: {items[0]}"
        assert "familyTip" in weather, "缺家属提示维度"
        record("14d.健康气象日报", True,
               f"weather={weather.get('weather')}({weather.get('weatherLabel')}), 指数={weather.get('index')}, "
               f"今日事项={len(items)}项(顶级={str(items[0].get('event'))[:18]})", ms)
    except AssertionError as e:
        record("14d.健康气象日报", False, str(e), 0)

    # ---------- 15. 医生通知（事件驱动链路验证） ----------
    try:
        data, ms = api.call("GET", "/api/notification/list")
        notifications = data if isinstance(data, list) else data.get("notifications", data.get("list", []))
        record("15.医生通知查询", True, f"通知数={len(notifications) if isinstance(notifications, list) else 'N/A'}", ms)
    except AssertionError as e:
        record("15.医生通知查询", False, str(e), 0)

    return summary()


def summary():
    total = len(RESULTS)
    passed = sum(1 for r in RESULTS if r["ok"])
    failed = total - passed
    print("\n" + "=" * 64)
    print(f"端到端测试结果: {passed}/{total} 通过" + (f"，失败 {failed} 项" if failed else "，全部通过"))
    print("=" * 64)
    report = {"total": total, "passed": passed, "failed": failed, "steps": RESULTS,
              "finishedAt": datetime.now().isoformat(timespec="seconds")}
    with open("e2e_report.json", "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print("详细报告已写入 e2e_report.json")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
