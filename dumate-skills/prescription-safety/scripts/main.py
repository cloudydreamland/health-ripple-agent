#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
智慧云脑诊疗闭环助手 - 处方安全审核 Skill 主脚本（含决策证据链与主动式拦截）

创新点：
1. 主动式拦截（Proactive Interception）- 智能体主动判定高风险并主动拦截，不等医生查询
2. 决策证据链（Decision Evidence Chain）- 每次处方审核生成可追溯证据链，可审计可申诉
3. 智能体交班（Agent Handoff）- 处方完成后交接给随访Agent，传递用药方案与随访重点
4. 安全边界自我约束 - 智能体明确"能拦截"但"不能开方"，最终开方权在医生

使用方式（由 DuMate AI 根据 SKILL.md 指令调用）:
    python scripts/main.py --action check --patient-id 1 --doctor-id 1 --diagnosis "急性上呼吸道感染" --drugs '[{"drugName":"阿莫西林","dosage":"0.5g","frequency":"每日三次","usageMethod":"口服"}]'
    python scripts/main.py --action notify --doctor-id 1 --message "高危处方拦截"
    python scripts/main.py --action save --patient-id 1 --medical-record-id 1 --risk-level LOW --drugs '[...]'
    python scripts/main.py --action drugs --keyword 阿莫西林
    python scripts/main.py --action evidence --decision-id PRESCRIPTION-2026-001  # 查询处方审核证据链
    python scripts/main.py --action handoff --prescription-id 1                   # 生成给随访Agent的交班

输出：标准 JSON，包含 evidenceChain 字段，供 DuMate AI 读取并按 SKILL.md 规则自主决策（拦截/警告/通过）。
"""

import argparse
import hashlib
import json
import os
import sys
import urllib.request
import urllib.error
from datetime import datetime


GATEWAY_URL = os.environ.get("SCB_GATEWAY_URL", "http://localhost:8080")
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
# 决策证据链核心实现（创新点2）
# ============================================================
def _build_evidence_chain(decision_type, inputs, ai_output, reasoning, alternatives, confidence, action_taken, counterfactual_tree=None):
    """构建决策证据链+反事实决策树，可审计可申诉。

    反事实决策树（XAI学术前沿）：记录"如果选了其他方案会怎样"
    - chosenPath: 选择的路径
    - alternativePaths: 被考虑/排除的其他路径及反事实后果预测
    """
    decision_id = f"{decision_type}-{datetime.now().strftime('%Y%m%d%H%M%S')}-{hashlib.md5(json.dumps(inputs, ensure_ascii=False).encode()).hexdigest()[:6]}"
    evidence = {
        "decisionId": decision_id,
        "decisionType": decision_type,
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "trigger": inputs.get("trigger", "doctor_request"),
        "inputs": inputs,
        "consideredFactors": reasoning,
        "alternativesConsidered": alternatives,
        "decision": ai_output,
        "confidence": confidence,
        "actionTaken": action_taken,
        "agentId": "prescription-safety-agent",
    }
    # 反事实决策树（核心创新·XAI学术前沿）
    if counterfactual_tree:
        evidence["counterfactualTree"] = counterfactual_tree
        evidence["chosenPath"] = counterfactual_tree.get("chosenPath", "")
        evidence["alternativePaths"] = counterfactual_tree.get("alternativePaths", [])
    evidence["hash"] = hashlib.sha256(json.dumps(evidence, ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:16]
    _persist_evidence(decision_id, evidence)
    return evidence


# ============================================================
# 反事实决策树构建（核心创新·XAI学术前沿）
# 记录"如果选了其他方案会怎样"的反事实推理
# ============================================================
# 常见过敏原与其相关药物类映射（用于反事实后果预测）
_ALLERGY_DRUG_MAP = {
    "青霉素": {
        "related": ["阿莫西林", "氨苄西林", "青霉素", "阿莫西林克拉维酸钾", "美洛西林", "哌拉西林"],
        "outcome": "可能发生青霉素过敏性反应，严重可致过敏性休克",
        "risk": "HIGH",
        "evidence": "阿莫西林等属青霉素类，含β-内酰胺环",
    },
    "头孢": {
        "related": ["头孢"],
        "outcome": "可能发生头孢类过敏反应",
        "risk": "HIGH",
        "evidence": "头孢类与青霉素β-内酰胺环结构相似，存在交叉过敏",
    },
    "磺胺": {
        "related": ["磺胺", "复方磺胺", "复方新诺明"],
        "outcome": "可能发生磺胺类过敏反应",
        "risk": "HIGH",
        "evidence": "磺胺类药物与过敏史直接冲突",
    },
    "阿司匹林": {
        "related": ["阿司匹林", "拜阿司匹林"],
        "outcome": "可能诱发阿司匹林哮喘或过敏反应",
        "risk": "HIGH",
        "evidence": "阿司匹林过敏史直接冲突",
    },
}


def _build_counterfactual_tree(chosen_path, drugs, allergy_history, risk_level, suggestions, interactions=None):
    """构建反事实决策树。

    记录每个被排除的方案及其反事实后果预测：
    - 若开过敏类药物 → 过敏性休克风险
    - 若不拦截高风险处方 → 严重不良反应
    - 替代方案的反事实（按建议执行的风险降低）
    """
    alternative_paths = []
    allergy_text = (allergy_history or "").strip()

    # 1. 基于过敏史生成反事实路径：若开了过敏类药物会怎样
    if allergy_text and allergy_text != "UNKNOWN":
        drug_list = []
        if isinstance(drugs, str):
            try:
                drug_list = json.loads(drugs)
            except json.JSONDecodeError:
                drug_list = []
        else:
            drug_list = drugs or []

        for drug in drug_list:
            drug_name = drug.get("drugName", "") if isinstance(drug, dict) else str(drug)
            for allergy_key, info in _ALLERGY_DRUG_MAP.items():
                if allergy_key in allergy_text and any(rd in drug_name for rd in info["related"]):
                    alternative_paths.append({
                        "path": f"开具{drug_name}",
                        "wasRejected": True,
                        "rejectionReason": f"患者{allergy_key}过敏，{drug_name}属{allergy_key}类",
                        "counterfactualOutcome": info["outcome"],
                        "riskIfChosen": info["risk"],
                        "evidence": f"患者病史过敏史={allergy_text}；{info['evidence']}",
                    })

    # 2. 若未拦截高风险处方的反事实
    if (risk_level or "").upper() == "HIGH":
        alternative_paths.append({
            "path": "不拦截，直接开方",
            "wasRejected": True,
            "rejectionReason": "高风险处方AI自主拦截",
            "counterfactualOutcome": "患者可能发生严重不良反应、过敏反应或药物相互作用，严重可致死",
            "riskIfChosen": "HIGH",
            "evidence": f"AI处方审核风险等级={risk_level}；药物相互作用={interactions or []}",
        })

    # 3. 若选了其他替代方案的反事实（基于建议）
    if suggestions:
        for suggestion in suggestions[:3]:
            alternative_paths.append({
                "path": f"采用替代方案：{suggestion}",
                "wasRejected": False,
                "rejectionReason": None,
                "counterfactualOutcome": "按AI建议执行，规避过敏/相互作用风险，安全性提升",
                "riskIfChosen": "LOW",
                "evidence": "AI审核建议的替代方案",
            })

    # 4. 若置信度低却强行决策的反事实
    if (risk_level or "").upper() == "MEDIUM":
        alternative_paths.append({
            "path": "AI不警告直接通过",
            "wasRejected": True,
            "rejectionReason": "MEDIUM风险需医生确认",
            "counterfactualOutcome": "可能漏掉潜在药物相互作用或禁忌，增加不良反应风险",
            "riskIfChosen": "MEDIUM",
            "evidence": f"AI审核风险等级={risk_level}，需人工复核",
        })

    tree = {
        "chosenPath": chosen_path,
        "alternativePaths": alternative_paths,
        "counterfactualCount": len(alternative_paths),
    }
    return tree


def _persist_evidence(decision_id, evidence):
    try:
        os.makedirs(EVIDENCE_STORE, exist_ok=True)
        with open(os.path.join(EVIDENCE_STORE, f"{decision_id}.json"), "w", encoding="utf-8") as f:
            json.dump(evidence, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


def _load_evidence(decision_id):
    try:
        with open(os.path.join(EVIDENCE_STORE, f"{decision_id}.json"), "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {"error": "evidence_not_found", "decisionId": decision_id}


# ============================================================
# 主动式拦截判定（创新点1）
# ============================================================
def _assess_proactive_interception(risk_level, allergy_conflict):
    """智能体主动判定是否需要主动拦截。

    主动式场景：
    - riskLevel=HIGH → 主动拦截，不等医生确认
    - 检测到过敏冲突 → 主动拦截（即使riskLevel未到HIGH）
    - 多重药物相互作用 → 主动预警
    """
    risk = (risk_level or "").upper()
    proactive = {"isProactive": False, "proactiveAction": None, "reason": None}

    if risk == "HIGH" or allergy_conflict:
        proactive.update({
            "isProactive": True,
            "proactiveAction": "PROACTIVE_INTERCEPT",
            "reason": f"智能体主动拦截：{'过敏冲突' if allergy_conflict else 'HIGH风险'}，不等医生查询，直接阻止开方并推送通知",
        })
    elif risk == "MEDIUM":
        proactive.update({
            "isProactive": True,
            "proactiveAction": "PROACTIVE_WARN",
            "reason": "智能体主动警告：MEDIUM风险，等待医生确认后方可开方",
        })
    return proactive


# ============================================================
# 智能体交班给随访Agent（创新点3）
# ============================================================
def _build_handoff_to_followup(drugs, risk_level, patient_id, diagnosis):
    """处方完成后，交接给随访Agent，传递用药方案与随访重点。"""
    drug_names = []
    try:
        drug_names = [d.get("drugName", "") for d in json.loads(drugs)] if isinstance(drugs, str) else [d.get("drugName", "") for d in drugs]
    except Exception:
        pass

    handoff = {
        "handoffId": f"handoff-rx-{datetime.now().strftime('%Y%m%d%H%M%S')}",
        "fromAgent": "prescription-safety-agent",
        "toAgent": "followup-plan-agent",
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "patientContext": {"patientId": patient_id, "diagnosis": diagnosis},
        "prescriptionSummary": {"drugs": drug_names, "riskLevel": risk_level},
        "attentions": [],
        "suggestedApproach": "请随访Agent根据用药方案生成随访计划，重点关注药物依从性与不良反应",
    }
    if (risk_level or "").upper() in ("HIGH", "MEDIUM"):
        handoff["attentions"].append("处方存在风险，随访需重点询问是否出现不良反应")
    return handoff


# ============================================================
# action: check（处方安全审核 - 核心动作，含证据链+主动式拦截）
# ============================================================
def action_check(args):
    """查询患者过敏史 + 调用处方审核API，生成证据链与主动式拦截判定。"""
    if not args.patient_id:
        return {"error": "missing_param", "message": "patient-id is required"}
    if not args.drugs:
        return {"error": "missing_param", "message": "drugs is required (JSON list)"}

    try:
        drugs = json.loads(args.drugs)
    except json.JSONDecodeError:
        return {"error": "invalid_param", "message": "drugs must be valid JSON array"}

    # Step1: 查询患者过敏史与既往史
    patient_summary = _http_get(f"/internal/patients/{args.patient_id}/summary")
    allergy_history = ""
    past_history = ""
    patient_age = None
    patient_gender = ""
    patient_name = ""
    if isinstance(patient_summary, dict):
        allergy_history = patient_summary.get("allergyHistory", "") or ""
        past_history = patient_summary.get("pastHistory", "") or ""
        patient_age = patient_summary.get("age")
        patient_gender = patient_summary.get("gender", "") or ""
        patient_name = patient_summary.get("name", "") or ""
    elif isinstance(patient_summary, dict) and patient_summary.get("error"):
        allergy_history = "UNKNOWN"

    # Step2: 调用处方审核API
    payload = {
        "patientId": _parse_int(args.patient_id),
        "doctorId": _parse_int(args.doctor_id),
        "medicalRecordId": _parse_int(args.medical_record_id),
        "diagnosis": args.diagnosis or "",
        "patientAge": patient_age,
        "patientGender": patient_gender,
        "allergyHistory": allergy_history,
        "pastHistory": past_history,
        "drugs": drugs,
    }
    payload = {k: v for k, v in payload.items() if v is not None}

    result = _http_post("/api/prescription/check", payload)

    if isinstance(result, dict) and "error" not in result:
        result["patientAllergyHistory"] = allergy_history
        result["patientName"] = patient_name
    else:
        result = {
            "riskLevel": "UNKNOWN",
            "riskDescription": "处方审核服务暂时不可用",
            "suggestions": "建议人工审核处方安全性",
            "interactions": [],
            "contraindications": [],
            "adjustmentSuggestions": [],
            "degraded": True,
            "patientAllergyHistory": allergy_history,
            "patientName": patient_name,
            "errorDetail": result,
        }

    # 创新点1：主动式拦截判定
    risk_level = result.get("riskLevel", "")
    # 检测过敏冲突：过敏史非空且contraindications或interactions含过敏相关
    allergy_conflict = bool(allergy_history and allergy_history != "UNKNOWN" and
                            (result.get("contraindications") or result.get("interactions")))
    proactive = _assess_proactive_interception(risk_level, allergy_conflict)
    result["proactiveAssessment"] = proactive

    # 创新点2：构建决策证据链 + 反事实决策树
    suggestions = result.get("adjustmentSuggestions", []) or []
    alternatives = [{"option": s, "rejectedReason": "替代方案待医生评估"} for s in suggestions[:3]]

    # 反事实决策树构建（核心创新·XAI学术前沿）
    risk_upper = (risk_level or "").upper()
    if risk_upper == "HIGH" or allergy_conflict:
        chosen_path = "AI自主拦截高风险处方" + ("并建议替代方案" if suggestions else "")
    elif risk_upper == "MEDIUM":
        chosen_path = "AI警告风险，等待医生确认后方可开方"
    else:
        chosen_path = "AI通过处方审核"

    counterfactual_tree = _build_counterfactual_tree(
        chosen_path=chosen_path,
        drugs=drugs,
        allergy_history=allergy_history,
        risk_level=risk_level,
        suggestions=suggestions,
        interactions=result.get("interactions", []),
    )

    evidence = _build_evidence_chain(
        decision_type="PRESCRIPTION_CHECK",
        inputs={
            "patientId": args.patient_id,
            "diagnosis": args.diagnosis,
            "drugs": [d.get("drugName", "") for d in drugs],
            "allergyHistory": allergy_history,
            "trigger": "doctor_prescription_request",
        },
        ai_output={
            "riskLevel": risk_level,
            "interactions": list(result.get("interactions", [])),
            "contraindications": list(result.get("contraindications", [])),
        },
        reasoning=[
            f"患者过敏史: {allergy_history or '无'}",
            f"AI审核风险等级: {risk_level}",
            f"药物相互作用: {result.get('interactions', [])}",
            f"禁忌: {result.get('contraindications', [])}",
        ],
        alternatives=alternatives,
        confidence=0.0 if result.get("degraded") else 0.85,
        action_taken=proactive["proactiveAction"] or "APPROVED",
        counterfactual_tree=counterfactual_tree,
    )
    result["evidenceChain"] = evidence
    result["counterfactualTree"] = counterfactual_tree
    return result


# ============================================================
# action: notify（推送风险通知）
# ============================================================
def action_notify(args):
    """推送风险通知给医生（通过通知服务）。"""
    if not (args.doctor_id and args.message):
        return {"error": "missing_param", "message": "doctor-id and message are required"}

    payload = {
        "doctorId": _parse_int(args.doctor_id),
        "type": "PRESCRIPTION_RISK",
        "title": "处方安全风险通知",
        "content": args.message,
        "level": "HIGH",
    }
    result = _http_post("/api/notification/create", payload)
    if isinstance(result, dict) and "error" in result:
        return {"success": False, "degraded": True, "errorDetail": result}
    return {"success": True, "notification": result}


# ============================================================
# action: save（保存通过审核的处方）
# ============================================================
def action_save(args):
    """保存处方到后端数据库。"""
    if not (args.patient_id and args.medical_record_id and args.drugs):
        return {"error": "missing_param", "message": "patient-id, medical-record-id, drugs are required"}

    try:
        drugs = json.loads(args.drugs)
    except json.JSONDecodeError:
        return {"error": "invalid_param", "message": "drugs must be valid JSON array"}

    payload = {
        "patientId": _parse_int(args.patient_id),
        "medicalRecordId": _parse_int(args.medical_record_id),
        "riskLevel": args.risk_level or "LOW",
        "drugs": drugs,
    }
    result = _http_post("/api/prescription/create", payload)
    if isinstance(result, dict) and "error" in result:
        return {"success": False, "degraded": True, "errorDetail": result}
    return {"success": True, "prescription": result}


# ============================================================
# action: drugs（查询药品信息）
# ============================================================
def action_drugs(args):
    """查询药品信息。"""
    if not args.keyword:
        return {"error": "missing_param", "message": "keyword is required"}
    path = "/api/search/drugs?q=" + urllib.parse.quote(args.keyword)
    result = _http_get(path)
    return result if result else {"drugs": [], "degraded": True}


# ============================================================
# action: evidence（查询决策证据链）- 创新演示入口
# ============================================================
def action_evidence(args):
    """查询处方审核决策证据链。"""
    if not args.decision_id:
        try:
            files = [f[:-5] for f in os.listdir(EVIDENCE_STORE) if f.endswith(".json")]
            return {"evidenceIds": files, "count": len(files)}
        except Exception:
            return {"evidenceIds": [], "count": 0}
    return _load_evidence(args.decision_id)


# ============================================================
# action: handoff（生成给随访Agent的交班）
# ============================================================
def action_handoff(args):
    """处方完成后，生成交班信息给随访Agent。"""
    if not args.prescription_id:
        return {"error": "missing_param", "message": "prescription-id is required"}
    handoff = _build_handoff_to_followup(
        drugs=args.drugs or "[]",
        risk_level=args.risk_level or "LOW",
        patient_id=args.patient_id,
        diagnosis=args.diagnosis,
    )
    handoff["prescriptionId"] = _parse_int(args.prescription_id)
    return handoff


def _parse_int(value):
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (ValueError, TypeError):
        return None


def build_parser():
    parser = argparse.ArgumentParser(description="智慧云脑处方安全审核 Skill（含决策证据链）")
    parser.add_argument("--action", required=True,
                        choices=["check", "notify", "save", "drugs", "evidence", "handoff"],
                        help="执行的动作：check=处方审核, notify=推送通知, save=保存处方, drugs=查药品, evidence=查证据链, handoff=生成交班")
    parser.add_argument("--gateway-url", default=None, help="后端网关地址，覆盖环境变量")

    parser.add_argument("--patient-id", default=None, help="患者ID")
    parser.add_argument("--doctor-id", default=None, help="医生ID")
    parser.add_argument("--medical-record-id", default=None, help="病历ID")
    parser.add_argument("--diagnosis", default=None, help="诊断")
    parser.add_argument("--drugs", default=None, help="药品列表 JSON")
    parser.add_argument("--risk-level", default=None, help="风险等级 HIGH/MEDIUM/LOW")
    parser.add_argument("--message", default=None, help="通知消息内容")
    parser.add_argument("--keyword", default=None, help="药品搜索关键词")

    parser.add_argument("--decision-id", default=None, help="决策证据链ID")
    parser.add_argument("--prescription-id", default=None, help="处方ID（生成交班时使用）")

    return parser


def main():
    global GATEWAY_URL
    parser = build_parser()
    args = parser.parse_args()

    if args.gateway_url:
        GATEWAY_URL = args.gateway_url
        os.environ["SCB_GATEWAY_URL"] = args.gateway_url

    if args.action == "check":
        result = action_check(args)
    elif args.action == "notify":
        result = action_notify(args)
    elif args.action == "save":
        result = action_save(args)
    elif args.action == "drugs":
        result = action_drugs(args)
    elif args.action == "evidence":
        result = action_evidence(args)
    elif args.action == "handoff":
        result = action_handoff(args)
    else:
        result = {"error": "unknown_action", "action": args.action}

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
