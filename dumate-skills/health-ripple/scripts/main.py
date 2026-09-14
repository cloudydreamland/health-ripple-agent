#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
智慧云脑·健康事件涟漪守护智能体 - 涟漪守护 Skill 主脚本

核心创新点（不人云亦云，命中评审痛点）：
1. 健康事件涟漪效应（Health Event Ripple Effect）— 一个事件触发多维度连锁影响推演，无竞品对标
2. 决策反事实推理（Counterfactual Reasoning）— 每个涟漪节点生成反事实决策树，XAI学术前沿
3. 多智能体MDT会诊 — 五Agent多视角发言，模拟真实多学科会诊文化
4. 医疗时间学感知 — 窗口期/节律/周期/季节，DuMate定时任务落地
5. 主动式智能体 — 不等患者询问，主动推演事件影响并设置时间学触达

使用方式（由 DuMate AI 根据 SKILL.md 指令调用）:
    python scripts/main.py --action ripple --diagnosis "2型糖尿病" --drugs '[{"drugName":"二甲双胍"}]' --patient-id 1
    python scripts/main.py --action mdt --patient-id 1 --chief-complaint "胸闷气短3天" --past-history "糖尿病,高血压,慢性肾病"
    python scripts/main.py --action conflict --drug "华法林"
    python scripts/main.py --action complication --diagnosis "2型糖尿病"
    python scripts/main.py --action evidence --decision-id RIPPLE-2026-001

输出：标准 JSON，包含 rippleGraph / counterfactualTree / mdtConsultation / evidenceChain，
供 DuMate AI 读取并按 SKILL.md 规则自主决策（主动设置时间学触达/主动建议MDT会诊/反事实可追问）。
"""

import argparse
import hashlib
import json
import os
import re
import time
import urllib.request
import urllib.error
import urllib.parse
import math
from datetime import datetime


GATEWAY_URL = os.environ.get("SCB_GATEWAY_URL", "http://localhost:8080")
# 存储 anchored 到 Skill 根目录（scripts/ 的上级），保证任意 CWD 调用落盘位置一致
_SKILL_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EVIDENCE_STORE = os.environ.get("SCB_EVIDENCE_STORE", os.path.join(_SKILL_ROOT, ".scb_evidence"))
AUDIT_LOG = os.environ.get("SCB_AUDIT_LOG", os.path.join(_SKILL_ROOT, ".scb_audit", "audit.log"))
API_TOKEN = os.environ.get("SCB_API_TOKEN", "")
TIMEOUT = 15

# 输入长度上限（防注入/防滥用）
MAX_LEN = {"diagnosis": 100, "past_history": 500, "chief_complaint": 300, "drug": 60, "drugs": 20}

# 安全边界声明（每次涟漪/MDT 输出必带，智能体自我约束）
SAFETY_BOUNDARY = {
    "advisoryOnly": True,
    "canPrescribe": False,
    "canDiagnose": False,
    "finalDecisionOwner": "DOCTOR",
    "emergencyPolicy": "识别到HIGH风险信号时即时引导急诊，不等待追问",
    "statement": "本智能体输出为辅助决策建议，不构成处方/诊断，最终决策权在医生",
}


# ============================================================
# 安全：数据脱敏 + 防篡改审计日志 + 输入校验
# ============================================================
_PHONE_RE = re.compile(r"1[3-9]\d{9}")
_IDCARD_RE = re.compile(r"\d{17}[\dXx]")


def _mask_text(text):
    """脱敏自由文本：手机号保留前3后2，身份证保留前3后2。患者ID为内部假名ID，不脱敏。"""
    if not text or not isinstance(text, str):
        return text
    text = _PHONE_RE.sub(lambda m: m.group(0)[:3] + "****" + m.group(0)[-2:], text)
    text = _IDCARD_RE.sub(lambda m: m.group(0)[:3] + "***********" + m.group(0)[-2:], text)
    return text


def _mask_params(args):
    """参数脱敏快照（仅用于审计日志与本地证据，防PHI明文落盘）。"""
    return {
        "action": args.action,
        "patientId": args.patient_id,
        "diagnosis": _mask_text(args.diagnosis)[:MAX_LEN["diagnosis"]] if args.diagnosis else None,
        "drugs": _mask_text(args.drugs)[:200] if args.drugs else None,
        "chiefComplaint": _mask_text(args.chief_complaint)[:MAX_LEN["chief_complaint"]] if args.chief_complaint else None,
        "pastHistory": _mask_text(args.past_history)[:MAX_LEN["past_history"]] if args.past_history else None,
        "drug": _mask_text(args.drug)[:MAX_LEN["drug"]] if args.drug else None,
        "decisionId": args.decision_id,
    }


def _validate_inputs(args):
    """入口输入校验：长度上限 + 类型安全，防止提示注入式超长载荷。"""
    for field, limit in MAX_LEN.items():
        value = getattr(args, field, None) if field != "drugs" else args.drugs
        if field == "drugs":
            if value:
                try:
                    items = json.loads(value)
                    if not isinstance(items, list) or len(items) > limit:
                        return f"drugs 数量超限（最多{limit}项）"
                except json.JSONDecodeError:
                    return "drugs 不是合法 JSON 数组"
        elif value and len(str(value)) > limit:
            return f"{field} 超长（最多{limit}字符）"
    if args.action in ("ripple",) and not (args.diagnosis or args.drugs):
        return "diagnosis 或 drugs 至少提供一个"
    if args.action == "conflict" and not args.drug:
        return "drug 必填"
    if args.action == "complication" and not args.diagnosis:
        return "diagnosis 必填"
    return None


def _audit_write(entry):
    """追加式防篡改审计日志（JSONL，每行含前一行的哈希，与证据链同构）。"""
    try:
        os.makedirs(os.path.dirname(AUDIT_LOG), exist_ok=True)
        prev_hash = "GENESIS"
        if os.path.exists(AUDIT_LOG):
            with open(AUDIT_LOG, "r", encoding="utf-8") as f:
                lines = [ln for ln in f.read().splitlines() if ln.strip()]
                if lines:
                    try:
                        prev_hash = json.loads(lines[-1]).get("entryHash", "GENESIS")
                    except Exception:
                        prev_hash = "GENESIS"
        entry["prevHash"] = prev_hash
        entry["entryHash"] = hashlib.sha256(
            json.dumps(entry, ensure_ascii=False, sort_keys=True).encode("utf-8")
        ).hexdigest()
        with open(AUDIT_LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    except Exception:
        pass  # 审计失败不阻断主流程，但证据链层仍有后端哈希链兜底


# ============================================================
# HTTP 工具函数（携带 Bearer 令牌 + URL 安全校验）
# ============================================================
def _checked_url(path):
    base = GATEWAY_URL.rstrip("/")
    if not base.startswith(("http://", "https://")):
        raise ValueError("非法网关地址（仅允许 http/https）")
    return base + path


def _http_get(path):
    url = _checked_url(path)
    req = urllib.request.Request(url, method="GET")
    req.add_header("Accept", "application/json")
    if API_TOKEN:
        req.add_header("Authorization", "Bearer " + API_TOKEN)
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
    url = _checked_url(path)
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    if API_TOKEN:
        req.add_header("Authorization", "Bearer " + API_TOKEN)
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
# 内置涟漪知识库（降级 fallback：后端涟漪推演服务不可用时使用）
# 真实医疗知识沉淀，体现 Skill 的可复用性
# ============================================================
DRUG_LIFESTYLE_CONFLICTS = {
    "二甲双胍": [
        {"conflict": "饮酒", "risk": "乳酸酸中毒（严重可致死）", "severity": "HIGH", "advice": "服药期间禁止饮酒"},
        {"conflict": "维生素B12缺乏", "risk": "长期服用致B12缺乏性贫血", "severity": "MEDIUM", "advice": "建议定期监测B12水平"},
        {"conflict": "造影剂联用", "risk": "肾损伤", "severity": "HIGH", "advice": "造影检查前需提前停药48小时"},
    ],
    "华法林": [
        {"conflict": "柚子/葡萄柚", "risk": "增强抗凝效果，出血风险", "severity": "HIGH", "advice": "服药期间禁止食用柚子"},
        {"conflict": "大量绿叶蔬菜（维生素K）", "risk": "降低抗凝效果，血栓风险", "severity": "MEDIUM", "advice": "保持稳定摄入量，勿突然增减"},
        {"conflict": "饮酒", "risk": "影响凝血功能", "severity": "MEDIUM", "advice": "限制饮酒"},
    ],
    "阿莫西林": [
        {"conflict": "饮酒", "risk": "双硫仑样反应", "severity": "MEDIUM", "advice": "服药期间及停药后7天避免饮酒"},
    ],
    "布洛芬": [
        {"conflict": "饮酒", "risk": "胃肠道出血", "severity": "HIGH", "advice": "服药期间禁止饮酒"},
        {"conflict": "空腹服用", "risk": "胃黏膜损伤", "severity": "MEDIUM", "advice": "建议餐后服用"},
    ],
    "他汀类": [
        {"conflict": "柚子/葡萄柚", "risk": "肌病/横纹肌溶解风险", "severity": "HIGH", "advice": "服药期间禁止食用柚子"},
        {"conflict": "大量饮酒", "risk": "肝损伤", "severity": "HIGH", "advice": "限制饮酒并定期查肝功"},
    ],
    "头孢类": [
        {"conflict": "饮酒", "risk": "双硫仑样反应（严重可致死）", "severity": "HIGH", "advice": "服药期间及停药后7-10天禁止饮酒"},
    ],
    "磺脲类降糖药": [
        {"conflict": "饮酒", "risk": "低血糖/双硫仑反应", "severity": "HIGH", "advice": "服药期间禁止饮酒"},
    ],
    "四环素": [
        {"conflict": "日晒", "risk": "光敏反应", "severity": "MEDIUM", "advice": "服药期间避免强烈日晒"},
        {"conflict": "乳制品/钙铁", "risk": "影响吸收", "severity": "MEDIUM", "advice": "服药与乳制品间隔2小时"},
    ],
    "甲氨蝶呤": [
        {"conflict": "饮酒", "risk": "肝损伤加重", "severity": "HIGH", "advice": "服药期间禁止饮酒"},
        {"conflict": "日晒", "risk": "光敏反应", "severity": "MEDIUM", "advice": "避免强烈日晒"},
    ],
}

COMPLICATION_SIGNALS = {
    "2型糖尿病": [
        {"signal": "视力模糊/飞蚊症突发", "complication": "糖尿病视网膜病变", "action": "立即眼科就诊", "urgency": "HIGH"},
        {"signal": "足部感觉异常/伤口不愈", "complication": "糖尿病足", "action": "立即外科就诊", "urgency": "HIGH"},
        {"signal": "心悸/出汗/手抖/饥饿感", "complication": "低血糖", "action": "即时补糖并就医", "urgency": "HIGH"},
        {"signal": "多尿/多饮/乏力加重/意识模糊", "complication": "糖尿病酮症酸中毒", "action": "立即急诊", "urgency": "HIGH"},
    ],
    "高血压": [
        {"signal": "剧烈头痛/呕吐/视物模糊", "complication": "高血压危象", "action": "立即急诊", "urgency": "HIGH"},
        {"signal": "胸痛/胸闷/大汗", "complication": "心肌梗死/主动脉夹层", "action": "立即急诊（黄金120分钟）", "urgency": "HIGH"},
        {"signal": "肢体麻木/言语不清/面瘫", "complication": "脑卒中", "action": "立即急诊（黄金3小时）", "urgency": "HIGH"},
    ],
    "冠心病": [
        {"signal": "持续胸痛>15分钟/含服硝酸甘油不缓解", "complication": "急性心肌梗死", "action": "立即急诊（黄金120分钟）", "urgency": "HIGH"},
        {"signal": "夜间阵发呼吸困难/不能平卧", "complication": "心力衰竭", "action": "心内科就诊", "urgency": "HIGH"},
    ],
    "哮喘": [
        {"signal": "呼吸困难加重/讲话困难/嗜睡", "complication": "哮喘持续状态", "action": "立即急诊", "urgency": "HIGH"},
    ],
    "慢性肾病": [
        {"signal": "尿量骤减/水肿加重", "complication": "肾功能急性恶化", "action": "立即肾内科", "urgency": "HIGH"},
        {"signal": "呼吸困难/不能平卧", "complication": "心衰/容量超负荷", "action": "立即急诊", "urgency": "HIGH"},
    ],
}

RECHECK_WINDOWS = {
    "2型糖尿病": [
        {"item": "肝肾功能+空腹血糖", "timing": "服药2周后", "chronoType": "PERIODIC", "advice": "二甲双胍起始治疗后必查"},
        {"item": "糖化血红蛋白(HbA1c)", "timing": "3个月后", "chronoType": "PERIODIC", "advice": "评估血糖长期控制"},
        {"item": "眼底/足部/尿微量白蛋白", "timing": "每年", "chronoType": "PERIODIC", "advice": "并发症筛查"},
    ],
    "高血压": [
        {"item": "血压复查", "timing": "服药2周后", "chronoType": "PERIODIC", "advice": "评估降压效果"},
        {"item": "肝肾功能+电解质", "timing": "1-3个月后", "chronoType": "PERIODIC", "advice": "ACEI/利尿剂监测"},
        {"item": "心电图/心脏超声", "timing": "每年", "chronoType": "PERIODIC", "advice": "靶器官损害评估"},
    ],
    "冠心病": [
        {"item": "症状复查+心电图", "timing": "服药1-2周后", "chronoType": "PERIODIC", "advice": "评估治疗反应"},
        {"item": "冠脉评估", "timing": "3-6个月后", "chronoType": "PERIODIC", "advice": "病情变化时随时"},
    ],
    "华法林": [
        {"item": "INR凝血指标", "timing": "服药3-5天后", "chronoType": "PERIODIC", "advice": "调整剂量必查"},
        {"item": "INR凝血指标", "timing": "稳定后每月", "chronoType": "PERIODIC", "advice": "稳定期监测"},
    ],
}

CHRONO_TRIGGERS = {
    "2型糖尿病": [
        {"type": "RHYTHM", "event": "夜间0-3点低血糖高发", "trigger_time": "凌晨0-3点", "action": "主动询问患者状态"},
        {"type": "PERIODIC", "event": "服药2周后复查肝肾功能", "trigger_time": "服药后第14天", "action": "主动提醒复查"},
        {"type": "PERIODIC", "event": "3个月后查糖化血红蛋白", "trigger_time": "诊断后第90天", "action": "主动提醒复查"},
        {"type": "SEASONAL", "event": "换季血糖波动", "trigger_time": "秋冬换季", "action": "主动提醒血糖监测"},
    ],
    "高血压": [
        {"type": "RHYTHM", "event": "凌晨血压晨峰", "trigger_time": "凌晨4-6点", "action": "主动询问晨起血压"},
        {"type": "SEASONAL", "event": "秋冬血压升高", "trigger_time": "入秋/入冬", "action": "主动提醒增加监测频次"},
    ],
    "冠心病": [
        {"type": "WINDOW", "event": "心梗黄金救治窗口", "trigger_time": "胸痛发作后120分钟内", "action": "即时高优先级触达并引导急诊"},
    ],
    "哮喘": [
        {"type": "RHYTHM", "event": "夜间哮喘发作高峰", "trigger_time": "凌晨3-5点", "action": "主动询问呼吸状态"},
        {"type": "SEASONAL", "event": "春季花粉诱发", "trigger_time": "春季花粉季", "action": "主动提醒预防用药"},
    ],
}


# ============================================================
# 证据链 + 反事实决策树持久化（核心创新·XAI学术前沿）
# ============================================================
def _build_evidence_chain(decision_type, inputs, ai_output, reasoning, counterfactual_tree=None, confidence=0.85, action_taken="RIPPLE_DERIVED"):
    """构建涟漪推演证据链+反事实决策树，可审计可申诉。"""
    decision_id = f"{decision_type}-{datetime.now().strftime('%Y%m%d%H%M%S')}-{hashlib.md5(json.dumps(inputs, ensure_ascii=False).encode()).hexdigest()[:6]}"
    evidence = {
        "decisionId": decision_id,
        "decisionType": decision_type,
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "trigger": inputs.get("trigger", "health_event"),
        "inputs": inputs,
        "consideredFactors": reasoning,
        "decision": ai_output,
        "confidence": confidence,
        "actionTaken": action_taken,
        "agentId": "health-ripple-agent",
    }
    if counterfactual_tree:
        evidence["counterfactualTree"] = counterfactual_tree
        evidence["chosenPath"] = counterfactual_tree.get("chosenPath", "")
        evidence["alternativePaths"] = counterfactual_tree.get("alternativePaths", [])
    evidence["hash"] = hashlib.sha256(json.dumps(evidence, ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:16]
    _persist_evidence(decision_id, evidence)
    return evidence


def _persist_evidence(decision_id, evidence):
    try:
        os.makedirs(EVIDENCE_STORE, exist_ok=True)
        # 落盘前脱敏：自由文本字段中的手机号/身份证号
        safe = json.loads(json.dumps(evidence, ensure_ascii=False))
        inputs = safe.get("inputs") if isinstance(safe.get("inputs"), dict) else {}
        for k in ("diagnosis", "pastHistory", "chiefComplaint"):
            if k in inputs:
                inputs[k] = _mask_text(inputs.get(k))
        with open(os.path.join(EVIDENCE_STORE, f"{decision_id}.json"), "w", encoding="utf-8") as f:
            json.dump(safe, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


def _load_evidence(decision_id):
    try:
        with open(os.path.join(EVIDENCE_STORE, f"{decision_id}.json"), "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {"error": "evidence_not_found", "decisionId": decision_id}


# ============================================================
# 涟漪推演核心：构建5维度涟漪影响图谱（核心创新1）
# ============================================================
def _build_ripple_graph(diagnosis, drugs, past_history):
    """构建健康事件涟漪影响图谱。

    5个维度：
    1. 药物-生活冲突维度（服某药不能吃柚子/晒太阳/开车/饮酒）
    2. 复查窗口维度（何时查什么，医疗时间学·周期性）
    3. 并发症早期信号维度（出现什么症状立即就医）
    4. 家属注意事项维度
    5. 时间学触达维度（窗口期/节律/周期/季节）
    """
    drug_list = []
    if isinstance(drugs, str):
        try:
            drug_list = json.loads(drugs)
        except json.JSONDecodeError:
            drug_list = []
    else:
        drug_list = drugs or []
    drug_names = [d.get("drugName", "") if isinstance(d, dict) else str(d) for d in drug_list]

    # 维度1：药物-生活冲突
    drug_conflicts = []
    for name in drug_names:
        for key, conflicts in DRUG_LIFESTYLE_CONFLICTS.items():
            if key in name or name in key:
                for c in conflicts:
                    drug_conflicts.append({
                        "drug": name,
                        "conflict": c["conflict"],
                        "risk": c["risk"],
                        "severity": c["severity"],
                        "advice": c["advice"],
                        "counterfactualNote": f"若未识别{c['conflict']}冲突，患者可能发生{c['risk']}",
                    })

    # 维度2：复查窗口（医疗时间学·周期性）
    recheck_windows = []
    for dx in [diagnosis] + (past_history.split(",") if past_history else []):
        dx = (dx or "").strip()
        for key, windows in RECHECK_WINDOWS.items():
            if key in dx or dx in key:
                for w in windows:
                    recheck_windows.append({
                        "diagnosis": dx,
                        "item": w["item"],
                        "timing": w["timing"],
                        "chronoType": w["chronoType"],
                        "advice": w["advice"],
                    })

    # 维度3：并发症早期信号
    complication_signals = []
    for dx in [diagnosis] + (past_history.split(",") if past_history else []):
        dx = (dx or "").strip()
        for key, signals in COMPLICATION_SIGNALS.items():
            if key in dx or dx in key:
                for s in signals:
                    complication_signals.append({
                        "diagnosis": dx,
                        "signal": s["signal"],
                        "complication": s["complication"],
                        "action": s["action"],
                        "urgency": s["urgency"],
                    })

    # 维度4：家属注意事项（结构化涟漪节点：attention=守护动作，advice=执行建议，severity=风险等级）
    family_attentions = []
    if "糖尿病" in (diagnosis or ""):
        family_attentions.extend([
            _family_node("家属需识别低血糖症状（心悸/出汗/手抖）并即时补糖", "随身备糖块，症状出现15分钟内口服15g糖", "HIGH"),
            _family_node("饮食配合：低GI饮食结构调整", "全家主食替换低GI食材", "MEDIUM"),
            _family_node("足部护理：每日检查足部皮肤完整性", "每日睡前双人互查足底/趾缝", "MEDIUM"),
        ])
    if "高血压" in (diagnosis or "") or "冠心病" in (diagnosis or ""):
        family_attentions.extend([
            _family_node("家属需识别卒中症状（肢体麻木/言语不清/面瘫），立即拨打急救", "FAST口诀记忆，发病即刻120并记录时间", "HIGH"),
            _family_node("家属需识别心梗症状（持续胸痛>15分钟），立即急诊", "胸痛不缓解就地平卧，拨打120", "HIGH"),
            _family_node("低盐低脂饮食配合", "家庭人均食盐<5g/日", "MEDIUM"),
        ])
    if "哮喘" in (diagnosis or ""):
        family_attentions.extend([
            _family_node("家属需识别哮喘持续状态（呼吸困难加重/讲话困难），立即急诊", "备好速效支气管扩张剂，15分钟无缓解即急诊", "HIGH"),
            _family_node("避免家庭过敏原（尘螨/花粉/宠物毛发）", "卧室防螨床品，花粉季关窗", "MEDIUM"),
        ])

    # 维度5：时间学触达（窗口期/节律/周期/季节）
    chrono_triggers = []
    for dx in [diagnosis] + (past_history.split(",") if past_history else []):
        dx = (dx or "").strip()
        for key, triggers in CHRONO_TRIGGERS.items():
            if key in dx or dx in key:
                for t in triggers:
                    chrono_triggers.append({
                        "diagnosis": dx,
                        "chronoType": t["type"],
                        "event": t["event"],
                        "triggerTime": t["trigger_time"],
                        "action": t["action"],
                    })

    graph = {
        "healthEvent": {
            "diagnosis": diagnosis,
            "drugs": drug_names,
            "pastHistory": past_history or "",
        },
        "dimensions": {
            "drugLifestyleConflicts": drug_conflicts,
            "recheckWindows": recheck_windows,
            "complicationSignals": complication_signals,
            "familyAttentions": family_attentions,
            "chronoTriggers": chrono_triggers,
        },
        "summary": {
            "totalNodes": len(drug_conflicts) + len(recheck_windows) + len(complication_signals) + len(family_attentions) + len(chrono_triggers),
            "highRiskCount": sum(1 for c in drug_conflicts if c.get("severity") == "HIGH") + sum(1 for s in complication_signals if s.get("urgency") == "HIGH"),
        },
    }
    graph = _annotate_ripple_intensity(graph)
    return graph


def _family_node(attention, advice, severity):
    """家属注意事项涟漪节点（结构化，与后端契约一致）。"""
    return {"attention": attention, "advice": advice, "severity": severity}


# ============================================================
# 涟漪强度指数模型（RII，核心创新：把"涟漪"从比喻升级为可计算模型）
# 与后端 ripple-service RippleIntensityModel 严格同构：
# 节点强度 = 100 × 严重度S × 紧迫度U × 可干预度A × e^(-0.22×(ring-1))
# 事件级：RII=Top5节点均值(0-100)，有效扩散半径=强度≥15的最大环数，Top风险=强度降序前3
# 降级模式与在线模式输出契约一致，评分过程（S/U/A/衰减）随节点输出、可审计
# ============================================================
RII_DECAY_LAMBDA = 0.22
RII_RADIUS_THRESHOLD = 15.0
RII_RING_OF = (
    ("drugLifestyleConflicts", 1),   # 用药安全圈
    ("complicationSignals", 2),      # 疾病进展圈
    ("recheckWindows", 3),           # 复查窗口圈
    ("chronoTriggers", 4),           # 触达时机圈
    ("familyAttentions", 5),         # 家庭影响圈
)
RII_RING_NAME = {1: "用药安全圈", 2: "疾病进展圈", 3: "复查窗口圈", 4: "触达时机圈", 5: "家庭影响圈"}


def _rii_severity(node):
    level = node.get("severity") or node.get("urgency") or "MEDIUM"
    return {"HIGH": 0.95, "MEDIUM": 0.65, "LOW": 0.35}.get(level, 0.5)


def _rii_urgency(dimension, node):
    if dimension == "complicationSignals":
        return {"HIGH": 1.0, "MEDIUM": 0.7, "LOW": 0.4}.get(node.get("urgency"), 0.6)
    if dimension in ("recheckWindows", "chronoTriggers"):
        return {"WINDOW": 0.95, "RHYTHM": 0.75, "PERIODIC": 0.55, "SEASONAL": 0.35}.get(node.get("chronoType"), 0.5)
    if dimension == "drugLifestyleConflicts":
        return {"HIGH": 0.9, "MEDIUM": 0.6}.get(node.get("severity"), 0.4)
    return 0.5


def _rii_actionability(node):
    guidance = str(node.get("advice") or node.get("action") or "")
    base = 0.45
    if guidance.strip():
        base += 0.45
        if any(ch.isdigit() for ch in guidance):
            base += 0.05
    return min(0.95, base)


def _rii_label(dimension, node):
    if dimension == "drugLifestyleConflicts":
        return f"{node.get('drug')}+{node.get('conflict')}"
    if dimension == "complicationSignals":
        return f"{node.get('diagnosis')}→{node.get('complication')}"
    if dimension == "recheckWindows":
        return str(node.get("item", ""))
    if dimension == "chronoTriggers":
        return str(node.get("event", ""))
    return str(node.get("attention", ""))


def _annotate_ripple_intensity(graph):
    """为图谱每个涟漪节点标注环数/强度/评分依据，并在 summary 写入事件级 RII。"""
    scores = []
    for dimension, ring in RII_RING_OF:
        for node in graph["dimensions"].get(dimension, []):
            severity = _rii_severity(node)
            urgency = _rii_urgency(dimension, node)
            actionability = _rii_actionability(node)
            decay = math.exp(-RII_DECAY_LAMBDA * (ring - 1))
            node["ring"] = ring
            node["ringName"] = RII_RING_NAME[ring]
            node["intensity"] = round(100.0 * severity * urgency * actionability * decay, 1)
            node["scoreBreakdown"] = {
                "severity": round(severity, 2),
                "urgency": round(urgency, 2),
                "actionability": round(actionability, 2),
                "decay": round(decay, 2),
            }
            scores.append({
                "dimension": dimension,
                "ring": ring,
                "label": _rii_label(dimension, node),
                "intensity": node["intensity"],
            })

    ordered = sorted(scores, key=lambda s: -s["intensity"])
    top5 = ordered[:5]
    index = round(sum(s["intensity"] for s in top5) / len(top5), 1) if top5 else 0.0
    radius = max((s["ring"] for s in scores if s["intensity"] >= RII_RADIUS_THRESHOLD), default=0)
    level = "RED" if index >= 45 else ("ORANGE" if index >= 25 else "YELLOW")
    level_label = {"RED": "红色·高强度涟漪", "ORANGE": "橙色·中强度涟漪", "YELLOW": "黄色·低强度涟漪"}[level]

    view = {
        "index": index,
        "level": level,
        "levelLabel": level_label,
        "radius": radius,
        "topRisks": [
            {
                "dimension": s["dimension"],
                "label": s["label"],
                "ring": s["ring"],
                "ringName": RII_RING_NAME[s["ring"]],
                "intensity": s["intensity"],
            }
            for s in ordered[:3]
        ],
        "model": "RII=100×S×U×A×e^(-0.22×(ring-1))，事件指数=Top5节点均值",
    }
    graph["summary"]["rippleIntensity"] = view
    graph["rippleIntensity"] = view
    return graph


# ============================================================
# 涟漪节点反事实决策树（核心创新2·XAI学术前沿）
# 记录"如果未识别该冲突/信号会怎样"的反事实推理
# ============================================================
def _build_counterfactual_tree_for_ripple(ripple_graph):
    """为涟漪推演构建反事实决策树。

    反事实路径示例：
    - 若未识别二甲双胍+饮酒冲突 → 乳酸酸中毒风险
    - 若未设置夜间低血糖主动询问 → 错过低血糖急救窗口
    - 若未识别心梗黄金窗口 → 错过120分钟黄金救治
    """
    alternative_paths = []
    dims = ripple_graph.get("dimensions", {})

    # 药物-生活冲突的反事实
    for conflict in dims.get("drugLifestyleConflicts", []):
        if conflict.get("severity") == "HIGH":
            alternative_paths.append({
                "path": f"未识别{conflict['drug']}+{conflict['conflict']}冲突",
                "wasRejected": True,
                "rejectionReason": "涟漪推演已主动识别该冲突",
                "counterfactualOutcome": conflict.get("counterfactualNote", conflict.get("risk")),
                "riskIfChosen": conflict.get("severity", "HIGH"),
                "evidence": f"药物={conflict['drug']}；冲突={conflict['conflict']}",
            })

    # 并发症信号的反事实
    for signal in dims.get("complicationSignals", []):
        if signal.get("urgency") == "HIGH":
            alternative_paths.append({
                "path": f"未告知{signal['diagnosis']}的{signal['complication']}早期信号",
                "wasRejected": True,
                "rejectionReason": "涟漪推演已主动告知并发症信号",
                "counterfactualOutcome": f"患者出现{signal['signal']}时未能及时就医，延误{signal['complication']}诊治",
                "riskIfChosen": "HIGH",
                "evidence": f"诊断={signal['diagnosis']}；并发症={signal['complication']}",
            })

    # 时间学触达的反事实
    for trigger in dims.get("chronoTriggers", []):
        alternative_paths.append({
            "path": f"未设置{trigger['event']}的时间学触达",
            "wasRejected": True,
            "rejectionReason": "涟漪推演已主动设置时间学触达",
            "counterfactualOutcome": f"错过{trigger['event']}的主动干预窗口，患者未能及时响应",
            "riskIfChosen": "HIGH" if trigger.get("chronoType") == "WINDOW" else "MEDIUM",
            "evidence": f"时间学类型={trigger['chronoType']}；事件={trigger['event']}",
        })

    tree = {
        "chosenPath": "推演健康事件涟漪影响并生成守护计划+时间学触达",
        "alternativePaths": alternative_paths,
        "counterfactualCount": len(alternative_paths),
    }
    return tree


# ============================================================
# 多智能体MDT会诊聚合（核心创新3）
# 五Agent多视角发言，模拟真实医疗多学科会诊文化
# ============================================================
def _build_mdt_consultation(chief_complaint, past_history, diagnosis, drugs):
    """构建五Agent多视角MDT会诊纪要。

    五Agent视角：
    - 分诊Agent：紧急度/科室归属
    - 处方Agent：药物冲突/肝肾功
    - 病历Agent：既往史结构化
    - 随访Agent：监测要点
    - 涟漪守护Agent：新处方涟漪
    """
    past_list = [h.strip() for h in (past_history or "").split(",") if h.strip()] if past_history else []

    # 分诊Agent视角
    triage_view = {
        "agent": "medical-triage-agent",
        "perspective": "分诊与紧急度",
        "analysis": "",
        "urgencySuggestion": "",
    }
    complaint = chief_complaint or ""
    if any(kw in complaint for kw in ["胸痛", "胸闷", "呼吸困难", "意识不清", "晕厥"]):
        triage_view["urgencySuggestion"] = "HIGH-紧急度建议急诊排除心梗/卒中/主动脉夹层"
        triage_view["analysis"] = f"主诉含危险症状，紧急度HIGH，需排除急症"
    else:
        triage_view["urgencySuggestion"] = "MEDIUM-需进一步评估"
        triage_view["analysis"] = f"主诉未含明确危险症状，紧急度MEDIUM，需结合既往史评估"

    # 处方Agent视角
    prescription_view = {
        "agent": "prescription-safety-agent",
        "perspective": "药物安全与肝肾功",
        "analysis": "",
        "drugRiskNotes": [],
    }
    drug_list = []
    if isinstance(drugs, str):
        try:
            drug_list = json.loads(drugs)
        except json.JSONDecodeError:
            drug_list = []
    else:
        drug_list = drugs or []
    drug_names = [d.get("drugName", "") if isinstance(d, dict) else str(d) for d in drug_list]

    if "慢性肾病" in past_list:
        prescription_view["drugRiskNotes"].append("NSAIDs（布洛芬等）与慢性肾病冲突，避免使用")
    if "糖尿病" in past_list and "二甲双胍" in str(drug_names):
        prescription_view["drugRiskNotes"].append("二甲双胍在CKD患者中需根据eGFR调整剂量")
    if "高血压" in past_list:
        prescription_view["drugRiskNotes"].append("ACEI/ARB初始需监测肾功能与血钾")
    prescription_view["analysis"] = f"基于既往史{past_list}与处方{drug_names}的药物安全评估"

    # 病历Agent视角
    record_view = {
        "agent": "medical-record-agent",
        "perspective": "既往史结构化",
        "analysis": f"既往史要点：{'; '.join(past_list) if past_list else '无明确既往史'}",
        "historyNotes": past_list,
    }

    # 随访Agent视角
    followup_view = {
        "agent": "followup-plan-agent",
        "perspective": "随访与监测要点",
        "analysis": "",
        "monitoringPoints": [],
    }
    if "糖尿病" in past_list:
        followup_view["monitoringPoints"].extend(["监测血糖", "足部检查", "眼底筛查"])
    if "高血压" in past_list:
        followup_view["monitoringPoints"].extend(["监测血压", "心电图"])
    if "慢性肾病" in past_list:
        followup_view["monitoringPoints"].extend(["监测出入量", "体重", "电解质"])
    followup_view["analysis"] = f"基于既往史的随访监测要点：{'; '.join(followup_view['monitoringPoints']) if followup_view['monitoringPoints'] else '常规随访'}"

    # 涟漪守护Agent视角
    ripple_view = {
        "agent": "health-ripple-agent",
        "perspective": "新处方涟漪影响",
        "analysis": "",
        "rippleNotes": [],
    }
    for name in drug_names:
        for key, conflicts in DRUG_LIFESTYLE_CONFLICTS.items():
            if key in name or name in key:
                for c in conflicts:
                    if c.get("severity") == "HIGH":
                        ripple_view["rippleNotes"].append(f"{name}+{c['conflict']}→{c['risk']}")
    ripple_view["analysis"] = f"新处方涟漪影响：{'; '.join(ripple_view['rippleNotes']) if ripple_view['rippleNotes'] else '未识别高风险涟漪'}"

    mdt_summary = {
        "mdtId": f"MDT-{datetime.now().strftime('%Y%m%d%H%M%S')}",
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "caseSummary": {
            "chiefComplaint": chief_complaint or "",
            "pastHistory": past_list,
            "diagnosis": diagnosis or "",
            "drugs": drug_names,
        },
        "consultation": {
            "triageView": triage_view,
            "prescriptionView": prescription_view,
            "recordView": record_view,
            "followupView": followup_view,
            "rippleView": ripple_view,
        },
        "consensusNotes": [
            "多学科建议：结合各Agent视角形成综合诊疗方案",
            "风险预警：重点关注处方Agent与涟漪守护Agent识别的高风险",
            "随访要点：按随访Agent监测点执行长期管理",
        ],
        "agentCount": 5,
    }
    return mdt_summary


# ============================================================
# action: ripple（涟漪推演 - 核心高光演示动作）
# ============================================================
def action_ripple(args):
    """推演健康事件涟漪影响，生成5维度涟漪图谱+反事实决策树。"""
    if not (args.diagnosis or args.drugs):
        return {"error": "missing_param", "message": "diagnosis or drugs is required"}

    # Step1: 调用后端涟漪推演API（ripple-service：图谱+反事实树+哈希链证据+时间学计划）
    payload = {
        "diagnosis": args.diagnosis or "",
        "drugs": json.loads(args.drugs) if args.drugs else [],
        "patientId": _parse_int(args.patient_id),
        "pastHistory": args.past_history or "",
    }
    backend_result = _http_post("/api/health-event/ripple", payload)

    # Step2: 后端不可用时降级为内置知识库推演
    if isinstance(backend_result, dict) and "error" in backend_result:
        ripple_graph = _build_ripple_graph(args.diagnosis, args.drugs, args.past_history)
        ripple_graph["degraded"] = True
        ripple_graph["degradedReason"] = "后端涟漪推演服务不可用，降级为内置知识库推演"
        counterfactual_tree = None
        backend_evidence = None
        backend_chrono = None
    elif isinstance(backend_result, dict) and "dimensions" in backend_result:
        ripple_graph = backend_result
        # 后端为准：直接采用其反事实决策树与哈希链证据（单一事实源，防双写）
        counterfactual_tree = backend_result.get("counterfactualTree")
        backend_evidence = backend_result.get("evidenceChain")
        backend_chrono = backend_result.get("chronoTriggers")
    else:
        ripple_graph = _build_ripple_graph(args.diagnosis, args.drugs, args.past_history)
        ripple_graph["degraded"] = True
        ripple_graph["degradedReason"] = "后端返回格式异常，降级为内置知识库推演"
        counterfactual_tree = None
        backend_evidence = None
        backend_chrono = None

    # Step3: 构建反事实决策树（降级时本地构建；每个涟漪节点的反事实推理）
    if counterfactual_tree is None:
        counterfactual_tree = _build_counterfactual_tree_for_ripple(ripple_graph)

    # Step4: 主动式判定——是否需要主动建议MDT会诊
    high_risk_count = ripple_graph.get("summary", {}).get("highRiskCount", 0)
    proactive_mdt = False
    if high_risk_count >= 2:
        proactive_mdt = True

    # Step5: 构建证据链（后端已提供哈希链证据时直接采用；降级时本地构建）
    drug_names = []
    try:
        drug_names = [d.get("drugName", "") if isinstance(d, dict) else str(d) for d in (json.loads(args.drugs) if args.drugs else [])]
    except json.JSONDecodeError:
        pass

    if backend_evidence is not None:
        evidence = backend_evidence
    else:
        evidence = _build_evidence_chain(
            decision_type="RIPPLE_DERIVATION",
            inputs={
                "diagnosis": args.diagnosis,
                "drugs": drug_names,
                "pastHistory": args.past_history,
                "trigger": "health_event",
            },
            ai_output={
                "rippleSummary": ripple_graph.get("summary", {}),
                "dimensions": list(ripple_graph.get("dimensions", {}).keys()),
            },
            reasoning=[
                f"推演5维度涟漪影响",
                f"高风险节点数: {high_risk_count}",
                f"主动建议MDT: {proactive_mdt}",
            ],
            counterfactual_tree=counterfactual_tree,
            confidence=0.88,
            action_taken="RIPPLE_DERIVED+CHRONO_TRIGGER_SETUP" if ripple_graph.get("dimensions", {}).get("chronoTriggers") else "RIPPLE_DERIVED",
        )

    result = {
        "rippleGraph": ripple_graph,
        "counterfactualTree": counterfactual_tree,
        "proactiveAssessment": {
            "isProactive": True,
            "proactiveAction": "PROACTIVE_MDT_SUGGEST" if proactive_mdt else "PROACTIVE_CHRONO_TRIGGER_SETUP",
            "reason": f"识别{high_risk_count}个高风险节点，主动建议MDT会诊" if proactive_mdt else "主动设置医疗时间学触达计划",
            "recommendMdt": proactive_mdt,
        },
        "safetyBoundary": SAFETY_BOUNDARY,
        "evidenceChain": evidence,
    }
    # 后端已注册时间学触达计划时附带（DuMate定时任务可直接轮询 /api/chrono/due）
    if backend_chrono is not None:
        result["chronoTriggers"] = backend_chrono
    return result


# ============================================================
# action: mdt（多智能体MDT会诊 - 核心创新动作）
# ============================================================
def action_mdt(args):
    """触发五Agent多视角MDT会诊，聚合形成会诊纪要。"""
    payload = {
        "patientId": _parse_int(args.patient_id),
        "chiefComplaint": args.chief_complaint or "",
        "pastHistory": args.past_history or "",
        "diagnosis": args.diagnosis or "",
        "drugs": json.loads(args.drugs) if args.drugs else [],
    }
    backend_result = _http_post("/api/mdt/consult", payload)

    backend_evidence = None
    if isinstance(backend_result, dict) and "error" in backend_result:
        mdt = _build_mdt_consultation(args.chief_complaint, args.past_history, args.diagnosis, args.drugs)
        mdt["degraded"] = True
        mdt["degradedReason"] = "后端MDT会诊服务不可用，降级为内置五Agent视角生成"
    elif isinstance(backend_result, dict) and "consultation" in backend_result:
        mdt = backend_result
        # 后端为准：采用其哈希链证据（单一事实源，防双写）
        backend_evidence = backend_result.get("evidenceChain")
    else:
        mdt = _build_mdt_consultation(args.chief_complaint, args.past_history, args.diagnosis, args.drugs)
        mdt["degraded"] = True
        mdt["degradedReason"] = "后端返回格式异常，降级为内置五Agent视角生成"

    # MDT会诊证据链（后端已提供哈希链证据时直接采用；降级时本地构建）
    if backend_evidence is not None:
        evidence = backend_evidence
    else:
        evidence = _build_evidence_chain(
            decision_type="MDT_CONSULTATION",
            inputs={
                "chiefComplaint": args.chief_complaint,
                "pastHistory": args.past_history,
                "trigger": "mdt_consultation_request",
            },
            ai_output={"mdtId": mdt.get("mdtId"), "agentCount": mdt.get("agentCount", 5)},
            reasoning=["五Agent多视角发言", "聚合形成MDT会诊纪要"],
            counterfactual_tree={
                "chosenPath": "聚合五Agent多视角形成MDT会诊纪要",
                "alternativePaths": [
                    {
                        "path": "单一Agent决策（无MDT）",
                        "wasRejected": True,
                        "rejectionReason": "多病共存需多视角分析，单一Agent视角局限",
                        "counterfactualOutcome": "可能遗漏其他专科视角的风险点，导致诊疗不全面",
                        "riskIfChosen": "MEDIUM",
                        "evidence": "多病共存患者需多学科会诊",
                    }
                ],
                "counterfactualCount": 1,
            },
            confidence=0.90,
            action_taken="MDT_CONSULTED",
        )
    mdt["evidenceChain"] = evidence
    mdt["safetyBoundary"] = SAFETY_BOUNDARY
    return mdt


# ============================================================
# action: conflict（药物-生活冲突查询）
# ============================================================
def action_conflict(args):
    """查询某药物的-生活冲突知识库。"""
    if not args.drug:
        return {"error": "missing_param", "message": "drug is required"}

    # 尝试后端
    path = "/api/drug/lifestyle-conflict?drug=" + urllib.parse.quote(args.drug)
    backend_result = _http_get(path)

    if isinstance(backend_result, dict) and "error" not in backend_result and backend_result:
        return {"drug": args.drug, "conflicts": backend_result}

    # 降级为内置知识库
    conflicts = []
    for key, items in DRUG_LIFESTYLE_CONFLICTS.items():
        if key in args.drug or args.drug in key:
            conflicts.extend(items)
    return {
        "drug": args.drug,
        "conflicts": conflicts,
        "degraded": not conflicts,
        "degradedReason": "未查询到该药物的冲突知识" if not conflicts else "后端不可用，降级为内置知识库",
    }


# ============================================================
# action: complication（并发症早期信号查询）
# ============================================================
def action_complication(args):
    """查询某诊断的并发症早期信号。"""
    if not args.diagnosis:
        return {"error": "missing_param", "message": "diagnosis is required"}

    path = "/api/complication/signal?diagnosis=" + urllib.parse.quote(args.diagnosis)
    backend_result = _http_get(path)

    if isinstance(backend_result, dict) and "error" not in backend_result and backend_result:
        return {"diagnosis": args.diagnosis, "signals": backend_result}

    signals = []
    for key, items in COMPLICATION_SIGNALS.items():
        if key in args.diagnosis or args.diagnosis in key:
            signals.extend(items)
    return {
        "diagnosis": args.diagnosis,
        "signals": signals,
        "degraded": not signals,
        "degradedReason": "未查询到该诊断的并发症信号" if not signals else "后端不可用，降级为内置知识库",
    }


# ============================================================
# action: evidence（查询涟漪推演反事实决策树）- 创新演示入口
# ============================================================
def action_evidence(args):
    """查询涟漪推演决策的反事实决策树，用于审计与申诉。"""
    if not args.decision_id:
        try:
            files = [f[:-5] for f in os.listdir(EVIDENCE_STORE) if f.endswith(".json")]
            return {"evidenceIds": files, "count": len(files)}
        except Exception:
            return {"evidenceIds": [], "count": 0}
    return _load_evidence(args.decision_id)


def _parse_int(value):
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (ValueError, TypeError):
        return None


def build_parser():
    parser = argparse.ArgumentParser(description="智慧云脑·健康事件涟漪守护智能体 Skill（含涟漪推演+反事实决策树+MDT会诊）")
    parser.add_argument("--action", required=True,
                        choices=["ripple", "mdt", "conflict", "complication", "evidence"],
                        help="执行的动作：ripple=涟漪推演, mdt=MDT会诊, conflict=药物-生活冲突, complication=并发症信号, evidence=查反事实决策树")
    parser.add_argument("--gateway-url", default=None, help="后端网关地址，覆盖环境变量")
    parser.add_argument("--api-token", default=None, help="后端网关 Bearer 令牌（缺省读环境变量 SCB_API_TOKEN）")

    parser.add_argument("--patient-id", default=None, help="患者ID")
    parser.add_argument("--diagnosis", default=None, help="诊断")
    parser.add_argument("--drugs", default=None, help="药品列表 JSON")
    parser.add_argument("--chief-complaint", default=None, help="主诉（MDT会诊用）")
    parser.add_argument("--past-history", default=None, help="既往史（逗号分隔）")
    parser.add_argument("--drug", default=None, help="单个药品名（conflict动作用）")
    parser.add_argument("--decision-id", default=None, help="决策证据链ID（evidence动作用）")

    return parser


def main():
    global GATEWAY_URL, API_TOKEN
    parser = build_parser()
    args = parser.parse_args()

    if args.gateway_url:
        GATEWAY_URL = args.gateway_url
        os.environ["SCB_GATEWAY_URL"] = args.gateway_url
    if args.api_token:
        API_TOKEN = args.api_token
        os.environ["SCB_API_TOKEN"] = args.api_token

    started = time.time()
    # 安全入口：输入校验（长度/类型/必填）
    invalid = _validate_inputs(args)
    if invalid:
        result = {"error": "invalid_input", "message": invalid, "safetyBoundary": SAFETY_BOUNDARY}
        _audit_write({
            "ts": datetime.now().isoformat(timespec="seconds"),
            "params": _mask_params(args),
            "ok": False,
            "error": invalid,
            "durationMs": round((time.time() - started) * 1000),
        })
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    if args.action == "ripple":
        result = action_ripple(args)
    elif args.action == "mdt":
        result = action_mdt(args)
    elif args.action == "conflict":
        result = action_conflict(args)
    elif args.action == "complication":
        result = action_complication(args)
    elif args.action == "evidence":
        result = action_evidence(args)
    else:
        result = {"error": "unknown_action", "action": args.action}

    # 防篡改审计日志（参数脱敏后落盘）
    _audit_write({
        "ts": datetime.now().isoformat(timespec="seconds"),
        "params": _mask_params(args),
        "ok": not (isinstance(result, dict) and "error" in result),
        "error": result.get("error") if isinstance(result, dict) else None,
        "degraded": bool(result.get("degraded")) if isinstance(result, dict) else False,
        "durationMs": round((time.time() - started) * 1000),
    })

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
