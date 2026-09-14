#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
智慧云脑诊疗闭环助手 - 智能分诊导诊 Skill 主脚本（含决策证据链与主动式触达）

创新点：
1. 决策证据链（Decision Evidence Chain）- 每个AI决策生成可追溯证据，可审计可申诉
2. 主动式触达（Proactive Engagement）- 智能体主动判定急诊并引导，不等用户查询
3. 智能体交班（Agent Handoff）- 生成交班信息给下游接诊Agent
4. 记忆驱动个性化 - 跨会话记住患者偏好与依从性（演示版用环境变量模拟）

使用方式（由 DuMate AI 根据 SKILL.md 指令调用）:
    python scripts/main.py --action triage --chief-complaint "发烧咳嗽3天" --symptoms "略有胸闷" --age 35 --gender 男
    python scripts/main.py --action schedule
    python scripts/main.py --action register --doctor-id 1 --department-id 2 --appointment-time "2026-09-10T09:00:00"
    python scripts/main.py --action evidence --decision-id triage-2026-001  # 查询决策证据链
    python scripts/main.py --action handoff --registration-id 1               # 生成给接诊Agent的交班

输出：标准 JSON，包含 evidenceChain 字段，供 DuMate AI 读取并按 SKILL.md 规则自主决策与格式化。
"""

import argparse
import hashlib
import json
import os
import sys
import time
import urllib.request
import urllib.error
import urllib.parse
from datetime import datetime


GATEWAY_URL = os.environ.get("SCB_GATEWAY_URL", "http://localhost:8080")
# 决策证据链本地存储（DuMate沙箱内可持久化）
EVIDENCE_STORE = os.environ.get("SCB_EVIDENCE_STORE", ".scb_evidence")
TIMEOUT = 15


def _http_get(path):
    url = GATEWAY_URL.rstrip("/") + path
    req = urllib.request.Request(url, method="GET")
    req.add_header("Accept", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            body = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return {"error": "http_error", "status": e.code, "message": _safe_read(e)}
    except urllib.error.URLError as e:
        return {"error": "network_error", "message": str(e.reason)}
    except Exception as e:
        return {"error": "request_failed", "message": str(e)}
    return _parse_result(body)


def _http_post(path, payload):
    url = GATEWAY_URL.rstrip("/") + path
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            body = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return {"error": "http_error", "status": e.code, "message": _safe_read(e)}
    except urllib.error.URLError as e:
        return {"error": "network_error", "message": str(e.reason)}
    except Exception as e:
        return {"error": "request_failed", "message": str(e)}
    return _parse_result(body)


def _safe_read(http_error):
    try:
        return http_error.read().decode("utf-8")[:500]
    except Exception:
        return ""


def _parse_result(body):
    try:
        result = json.loads(body)
    except json.JSONDecodeError:
        return {"error": "json_parse_failed", "raw": body[:500]}
    if isinstance(result, dict) and "code" in result:
        if result.get("code") == 200:
            return result.get("data")
        return {"error": "business_error", "code": result.get("code"), "message": result.get("message")}
    return result


# ============================================================
# 决策证据链核心实现（创新点1）
# ============================================================
def _build_evidence_chain(decision_type, inputs, ai_output, reasoning, alternatives, confidence, action_taken, counterfactual_tree=None):
    """构建决策证据链+反事实决策树。

    证据链要素：
    - decisionId: 决策唯一ID（哈希生成，可追溯）
    - timestamp: 决策时间
    - trigger: 触发条件（什么导致这次决策）
    - inputs: 输入证据（患者主诉、过敏史等）
    - consideredFactors: 考虑的因素
    - alternatives: 被排除的其他选项及排除理由
    - decision: 最终决策
    - confidence: 置信度
    - actionTaken: 智能体执行的动作（自主拦截/通过/警告）
    - counterfactualTree: 反事实决策树（XAI学术前沿，记录"如果选其他方案会怎样"）
    - hash: 证据链哈希（防篡改，可审计）
    """
    decision_id = f"{decision_type}-{datetime.now().strftime('%Y%m%d%H%M%S')}-{hashlib.md5(json.dumps(inputs, ensure_ascii=False).encode()).hexdigest()[:6]}"

    evidence = {
        "decisionId": decision_id,
        "decisionType": decision_type,
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "trigger": inputs.get("trigger", "user_request"),
        "inputs": inputs,
        "consideredFactors": reasoning,
        "alternativesConsidered": alternatives,
        "decision": ai_output,
        "confidence": confidence,
        "actionTaken": action_taken,
        "agentId": "medical-triage-agent",
    }
    # 反事实决策树（核心创新·XAI学术前沿）
    if counterfactual_tree:
        evidence["counterfactualTree"] = counterfactual_tree
        evidence["chosenPath"] = counterfactual_tree.get("chosenPath", "")
        evidence["alternativePaths"] = counterfactual_tree.get("alternativePaths", [])
    # 证据链哈希（防篡改）
    evidence["hash"] = hashlib.sha256(json.dumps(evidence, ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:16]

    # 持久化到本地（DuMate沙箱可访问）
    _persist_evidence(decision_id, evidence)
    return evidence


# ============================================================
# 反事实决策树构建（核心创新·XAI学术前沿）
# 记录"如果选了其他科室/紧急度会怎样"的反事实推理
# ============================================================
def _build_counterfactual_tree(chosen_path, recommended_dept, urgency_level, confidence, chief_complaint):
    """构建分诊反事实决策树。

    分诊场景反事实路径：
    - 若分诊到急诊（但实际非急诊）→ 过度占用急诊资源
    - 若分诊到全科（但症状更匹配专科）→ 可能延误专科诊治
    - 若不引导急诊但实为急症 → 错过黄金救治窗口
    - 若AI置信度低却强行决策 → 可能分错科
    """
    alternative_paths = []
    urgency = (urgency_level or "ROUTINE").upper()
    rec_dept = recommended_dept or ""
    chief = chief_complaint or ""

    # 危险症状关键词
    danger_keywords = ["胸痛", "呼吸困难", "意识不清", "晕厥", "大出血", "抽搐", "窒息", "言语不清", "偏瘫", "面瘫"]
    has_danger = any(kw in chief for kw in danger_keywords)

    # 1. 若分诊到急诊的反事实（非急诊却分到急诊）
    if urgency != "EMERGENCY":
        alternative_paths.append({
            "path": "分诊至急诊",
            "wasRejected": True,
            "rejectionReason": f"紧急度判定为{urgency}，未达急诊指征",
            "counterfactualOutcome": "过度占用急诊资源，延误真正急诊患者；但若实际为急症则错过黄金救治窗口",
            "riskIfChosen": "MEDIUM",
            "evidence": f"AI紧急度判定={urgency}；置信度={confidence}",
        })

    # 2. 若分诊到全科的反事实（症状更匹配专科）
    if rec_dept and "全科" not in rec_dept:
        alternative_paths.append({
            "path": "分诊至全科门诊",
            "wasRejected": True,
            "rejectionReason": f"症状更匹配{rec_dept}",
            "counterfactualOutcome": "可能延误专科诊治，患者需二次转诊，增加等待时间",
            "riskIfChosen": "MEDIUM",
            "evidence": f"AI推荐科室={rec_dept}，主诉匹配专科特征",
        })

    # 3. 若不引导急诊但实为急症的反事实（最高风险反事实）
    if not has_danger and urgency != "EMERGENCY":
        alternative_paths.append({
            "path": "未识别潜在急症直接分诊门诊",
            "wasRejected": False,
            "rejectionReason": None,
            "counterfactualOutcome": "若实际为心梗/卒中/脓毒症等急症，错过黄金救治窗口（心梗120分钟/卒中3小时）",
            "riskIfChosen": "HIGH",
            "evidence": "AI已排除危险症状关键词，但需关注病情进展",
        })

    # 4. 若AI置信度低却强行决策的反事实
    if confidence is not None and confidence < 0.6:
        alternative_paths.append({
            "path": "AI强行决策不提示人工复核",
            "wasRejected": True,
            "rejectionReason": f"AI置信度{confidence}低于阈值0.6，主动建议人工复核",
            "counterfactualOutcome": "可能分错科室，导致患者二次排队与延误",
            "riskIfChosen": "MEDIUM",
            "evidence": f"AI置信度={confidence}，低于0.6阈值",
        })

    tree = {
        "chosenPath": chosen_path,
        "alternativePaths": alternative_paths,
        "counterfactualCount": len(alternative_paths),
    }
    return tree


def _persist_evidence(decision_id, evidence):
    """将证据链持久化到本地文件，可审计可申诉。"""
    try:
        os.makedirs(EVIDENCE_STORE, exist_ok=True)
        path = os.path.join(EVIDENCE_STORE, f"{decision_id}.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(evidence, f, ensure_ascii=False, indent=2)
    except Exception:
        pass  # 持久化失败不影响主流程


def _load_evidence(decision_id):
    """读取决策证据链。"""
    path = os.path.join(EVIDENCE_STORE, f"{decision_id}.json")
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {"error": "evidence_not_found", "decisionId": decision_id}


# ============================================================
# 主动式触达判定（创新点2）
# ============================================================
def _assess_proactive_action(triage_result, chief_complaint):
    """智能体主动判定是否需要主动触达（不等用户查询）。

    主动式场景：
    - urgencyLevel=EMERGENCY → 主动引导急诊，不等用户选择号源
    - 患者描述含"胸痛/呼吸困难/意识不清"等危险词 → 主动触发急诊通道
    - 置信度<0.6 → 主动建议人工复核（不强行AI决策）
    """
    urgency = (triage_result.get("urgencyLevel") or "ROUTINE").upper() if isinstance(triage_result, dict) else "ROUTINE"
    confidence = triage_result.get("confidence") or 0.0 if isinstance(triage_result, dict) else 0.0

    # 危险症状关键词主动识别
    danger_keywords = ["胸痛", "呼吸困难", "意识不清", "晕厥", "大出血", "抽搐", "窒息", "言语不清", "偏瘫", "面瘫"]
    chief = chief_complaint or ""
    has_danger = any(kw in chief for kw in danger_keywords)

    proactive = {
        "isProactive": False,
        "proactiveAction": None,
        "reason": None,
    }

    if has_danger or urgency == "EMERGENCY":
        proactive.update({
            "isProactive": True,
            "proactiveAction": "PROACTIVE_EMERGENCY_REDIRECT",
            "reason": f"智能体主动判定急诊指征：{'危险症状关键词' if has_danger else 'AI判定EMERGENCY'}，不等用户选择号源，直接引导急诊通道",
        })
    elif confidence < 0.6 and not triage_result.get("degraded"):
        proactive.update({
            "isProactive": True,
            "proactiveAction": "PROACTIVE_REQUEST_HUMAN_REVIEW",
            "reason": f"AI置信度{confidence}低于阈值0.6，主动建议人工导诊复核，不强行AI决策",
        })

    return proactive


# ============================================================
# 智能体交班（创新点3）
# ============================================================
def _build_handoff(triage_result, registration_result, patient_context):
    """生成给下游接诊Agent的交班信息，模拟医疗团队交接班文化。"""
    handoff = {
        "handoffId": f"handoff-{datetime.now().strftime('%Y%m%d%H%M%S')}",
        "fromAgent": "medical-triage-agent",
        "toAgent": "medical-record-agent",
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "patientContext": patient_context,
        "triageSummary": {
            "recommendedDepartment": triage_result.get("recommendedDepartment", "") if isinstance(triage_result, dict) else "",
            "urgencyLevel": triage_result.get("urgencyLevel", "") if isinstance(triage_result, dict) else "",
            "confidence": triage_result.get("confidence", 0) if isinstance(triage_result, dict) else 0,
            "reason": triage_result.get("reason", "") if isinstance(triage_result, dict) else "",
        },
        "registrationInfo": registration_result if registration_result else None,
        "attentions": [],  # 交接注意事项
        "suggestedApproach": "请接诊Agent基于分诊结果生成病历草稿，重点关注主诉与分诊理由一致性",
    }

    # 根据分诊结果生成交接注意事项
    if isinstance(triage_result, dict):
        urgency = (triage_result.get("urgencyLevel") or "").upper()
        if urgency in ("EMERGENCY", "URGENT"):
            handoff["attentions"].append("患者紧急度高，接诊需优先处理，建议简化问诊流程")
        if triage_result.get("confidence", 1.0) < 0.7:
            handoff["attentions"].append("分诊置信度较低，接诊时请复核科室归属")

    return handoff


# ============================================================
# action: triage（AI 分诊，含证据链+主动式触达）
# ============================================================
def action_triage(args):
    """调用后端 /api/triage/consult 进行 AI 分诊，生成决策证据链。"""
    if not args.chief_complaint:
        return {"error": "missing_param", "message": "chief-complaint is required for triage"}

    payload = {
        "patientId": _parse_int(args.patient_id),
        "chiefComplaint": args.chief_complaint,
        "symptoms": args.symptoms or "",
        "age": _parse_int(args.age),
        "gender": args.gender or "",
        "allergyHistory": args.allergy_history or "",
        "pastHistory": args.past_history or "",
    }
    payload = {k: v for k, v in payload.items() if v is not None}

    result = _http_post("/api/triage/consult", payload)
    if isinstance(result, dict) and "error" in result:
        # 降级：返回降级标记 + 证据链
        degraded_result = {
            "recommendedDepartment": "",
            "departmentCode": "",
            "urgencyLevel": "ROUTINE",
            "confidence": 0.0,
            "recommendedDoctorIds": [],
            "reason": "AI 分诊服务暂时不可用，请人工导诊",
            "degraded": True,
            "errorDetail": result,
        }
        evidence = _build_evidence_chain(
            decision_type="TRIAGE",
            inputs={"chiefComplaint": args.chief_complaint, "trigger": "user_request", "serviceAvailable": False},
            ai_output={"degraded": True, "reason": "AI分诊服务不可用"},
            reasoning=["AI分诊服务不可用", "降级为人工导诊"],
            alternatives=[{"option": "等待AI恢复", "rejected": "影响患者体验"}],
            confidence=0.0,
            action_taken="DEGRADED_TO_MANUAL",
        )
        degraded_result["evidenceChain"] = evidence
        return degraded_result

    # 创新点2：主动式触达判定
    proactive = _assess_proactive_action(result, args.chief_complaint)
    result["proactiveAssessment"] = proactive

    # 创新点1：构建决策证据链 + 反事实决策树
    alternatives = [
        {"option": "全科门诊", "rejectedReason": f"症状更匹配{result.get('recommendedDepartment', '')}"},
    ]
    if result.get("urgencyLevel", "").upper() != "EMERGENCY":
        alternatives.append({"option": "急诊", "rejectedReason": f"紧急度判定为{result.get('urgencyLevel', '')}，未达急诊指征"})

    # 反事实决策树构建（核心创新·XAI学术前沿）
    rec_dept = result.get("recommendedDepartment", "")
    urgency_val = result.get("urgencyLevel", "ROUTINE")
    confidence_val = result.get("confidence", 0.0)
    if (urgency_val or "").upper() == "EMERGENCY":
        chosen_path = f"AI主动引导急诊通道（{rec_dept}预备）"
    else:
        chosen_path = f"AI分诊至{rec_dept}，紧急度{urgency_val}"

    counterfactual_tree = _build_counterfactual_tree(
        chosen_path=chosen_path,
        recommended_dept=rec_dept,
        urgency_level=urgency_val,
        confidence=confidence_val,
        chief_complaint=args.chief_complaint,
    )

    evidence = _build_evidence_chain(
        decision_type="TRIAGE",
        inputs={
            "chiefComplaint": args.chief_complaint,
            "symptoms": args.symptoms or "",
            "age": args.age,
            "gender": args.gender,
            "allergyHistory": args.allergy_history,
            "pastHistory": args.past_history,
            "trigger": "user_request",
        },
        ai_output={
            "recommendedDepartment": rec_dept,
            "urgencyLevel": urgency_val,
            "confidence": confidence_val,
        },
        reasoning=[
            f"主诉匹配{rec_dept}科室特征",
            f"AI置信度{confidence_val}",
            f"紧急度判定{urgency_val}",
        ],
        alternatives=alternatives,
        confidence=confidence_val,
        action_taken=proactive["proactiveAction"] or "RECOMMEND_DEPARTMENT",
        counterfactual_tree=counterfactual_tree,
    )
    result["evidenceChain"] = evidence
    result["counterfactualTree"] = counterfactual_tree

    return result


# ============================================================
# action: schedule（查询排班号源）
# ============================================================
def action_schedule(args):
    """调用后端 /api/doctor/department/list 与 /api/doctor/list 查询科室与排班。"""
    departments = _http_get("/api/doctor/department/list")
    doctors = _http_get("/api/doctor/list")

    dept_list = departments if isinstance(departments, list) else []
    doctor_list = doctors if isinstance(doctors, list) else []

    if args.department_id:
        doctor_list = [d for d in doctor_list if str(d.get("departmentId")) == str(args.department_id)]

    return {
        "departments": dept_list,
        "doctors": doctor_list,
        "degraded": not dept_list and not doctor_list,
    }


# ============================================================
# action: register（预约挂号，含智能体交班生成）
# ============================================================
def action_register(args):
    """调用后端 /api/registration/create 完成挂号，并生成给接诊Agent的交班信息。"""
    if not (args.doctor_id and args.department_id and args.appointment_time):
        return {"error": "missing_param", "message": "doctor-id, department-id, appointment-time are required"}

    payload = {
        "doctorId": _parse_int(args.doctor_id),
        "departmentId": _parse_int(args.department_id),
        "appointmentTime": args.appointment_time,
        "triageRecordId": _parse_int(args.triage_record_id),
        "slotId": _parse_int(args.slot_id),
    }
    payload = {k: v for k, v in payload.items() if v is not None}

    result = _http_post("/api/registration/create", payload)
    if isinstance(result, dict) and "error" in result:
        return {"success": False, "degraded": True, "errorDetail": result}

    # 创新点3：生成给接诊Agent的交班信息
    patient_context = {
        "patientId": args.patient_id,
        "chiefComplaint": args.chief_complaint or "",
        "triageRecordId": args.triage_record_id,
    }
    triage_summary = {"recommendedDepartment": "", "urgencyLevel": "ROUTINE", "confidence": 0.0, "reason": ""}
    handoff = _build_handoff(triage_summary, result, patient_context)

    # 交班证据链
    handoff_evidence = _build_evidence_chain(
        decision_type="AGENT_HANDOFF",
        inputs={"fromAgent": "triage", "toAgent": "record", "registrationId": result.get("registrationId") if isinstance(result, dict) else None},
        ai_output=handoff,
        reasoning=["分诊完成，挂号成功，交接给接诊Agent生成病历"],
        alternatives=[],
        confidence=1.0,
        action_taken="HANDOFF_TO_RECORD_AGENT",
    )
    handoff["evidenceChain"] = handoff_evidence

    return {"success": True, "registration": result, "handoff": handoff}


# ============================================================
# action: evidence（查询决策证据链）- 创新点1演示入口
# ============================================================
def action_evidence(args):
    """查询指定决策的证据链，用于审计与申诉。"""
    if not args.decision_id:
        # 列出全部证据
        try:
            files = [f[:-5] for f in os.listdir(EVIDENCE_STORE) if f.endswith(".json")]
            return {"evidenceIds": files, "count": len(files)}
        except Exception:
            return {"evidenceIds": [], "count": 0}
    return _load_evidence(args.decision_id)


# ============================================================
# action: handoff（显式生成交班信息）
# ============================================================
def action_handoff(args):
    """显式生成交班信息。"""
    if not args.registration_id:
        return {"error": "missing_param", "message": "registration-id is required"}
    # 简化版：返回交班模板
    handoff = _build_handoff(
        triage_result={"recommendedDepartment": "", "urgencyLevel": "ROUTINE", "confidence": 0.0, "reason": ""},
        registration_result={"registrationId": _parse_int(args.registration_id)},
        patient_context={"registrationId": args.registration_id},
    )
    return handoff


def _parse_int(value):
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (ValueError, TypeError):
        return None


def build_parser():
    parser = argparse.ArgumentParser(description="智慧云脑智能分诊导诊 Skill（含决策证据链）")
    parser.add_argument("--action", required=True,
                        choices=["triage", "schedule", "register", "evidence", "handoff"],
                        help="执行的动作：triage=AI分诊, schedule=查号源, register=预约挂号, evidence=查证据链, handoff=生成交班")
    parser.add_argument("--gateway-url", default=None, help="后端网关地址，覆盖环境变量")

    parser.add_argument("--patient-id", default=None, help="患者ID（可选）")
    parser.add_argument("--chief-complaint", default=None, help="主诉（分诊必填）")
    parser.add_argument("--symptoms", default=None, help="症状描述")
    parser.add_argument("--age", default=None, help="年龄")
    parser.add_argument("--gender", default=None, help="性别")
    parser.add_argument("--allergy-history", default=None, help="过敏史")
    parser.add_argument("--past-history", default=None, help="既往史")

    parser.add_argument("--department-id", default=None, help="科室ID（查号源时可选筛选）")

    parser.add_argument("--doctor-id", default=None, help="医生ID（挂号必填）")
    parser.add_argument("--appointment-time", default=None, help="预约时间 ISO格式 如 2026-09-10T09:00:00")
    parser.add_argument("--triage-record-id", default=None, help="分诊记录ID（可选）")
    parser.add_argument("--slot-id", default=None, help="号源ID（可选）")

    parser.add_argument("--decision-id", default=None, help="决策证据链ID（查询证据链时使用）")
    parser.add_argument("--registration-id", default=None, help="挂号ID（生成交班时使用）")

    return parser


def main():
    global GATEWAY_URL
    parser = build_parser()
    args = parser.parse_args()

    if args.gateway_url:
        GATEWAY_URL = args.gateway_url
        os.environ["SCB_GATEWAY_URL"] = args.gateway_url

    if args.action == "triage":
        result = action_triage(args)
    elif args.action == "schedule":
        result = action_schedule(args)
    elif args.action == "register":
        result = action_register(args)
    elif args.action == "evidence":
        result = action_evidence(args)
    elif args.action == "handoff":
        result = action_handoff(args)
    else:
        result = {"error": "unknown_action", "action": args.action}

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
