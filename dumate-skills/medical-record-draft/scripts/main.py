#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
智慧云脑诊疗闭环助手 - 病历草稿生成 Skill 主脚本
作为 DuMate Skill 的 scripts/main.py，通过 HTTP 调用 smart_cloud_brain 后端 API。

使用方式（由 DuMate AI 根据 SKILL.md 指令调用）:
    python scripts/main.py --action generate --registration-id 1 --dialogue-text "患者诉发热咳嗽3天…" --department-code "RES"
    python scripts/main.py --action history --patient-id 1
    python scripts/main.py --action save --registration-id 1 --chief-complaint "发热咳嗽3天" --diagnosis "急性上呼吸道感染" --present-illness "..." --treatment-advice "..." --ai-generated true

输出：标准 JSON，供 DuMate AI 读取并按 SKILL.md 规则组织病历草稿卡片。
"""

import argparse
import json
import os
import sys
import urllib.request
import urllib.error
import urllib.parse
# 中文输出在任意终端/沙箱按 UTF-8 编码（Windows 控制台默认 GBK 会导致乱码）
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')


GATEWAY_URL = os.environ.get("SCB_GATEWAY_URL", "http://localhost:18080")
# 病历生成/保存接口按 DOCTOR 角色鉴权，优先取医生令牌
API_TOKEN = os.environ.get("SCB_API_TOKEN_DOCTOR") or os.environ.get("SCB_API_TOKEN", "")
TIMEOUT = 20  # 病历生成耗时较长


def _http_get(path):
    url = GATEWAY_URL.rstrip("/") + path
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
    url = GATEWAY_URL.rstrip("/") + path
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
        if result.get("code") in (0, 200):
            return result.get("data")
        return {"error": "business_error", "code": result.get("code"), "message": result.get("message")}
    return result


# ============================================================
# action: generate（生成病历草稿）
# ============================================================
def action_generate(args):
    """调用后端 /api/medical-record/generate 生成结构化病历草稿。"""
    if not (args.registration_id and args.dialogue_text):
        return {"error": "missing_param", "message": "registration-id and dialogue-text are required"}

    payload = {
        "registrationId": _parse_int(args.registration_id),
        "departmentCode": args.department_code or "",
        "dialogueText": args.dialogue_text,
    }
    result = _http_post("/api/medical-record/generate", payload)
    if isinstance(result, dict) and "error" in result:
        # 分级降级：改用内置知识库从问诊对话中确定性提取（不推断未提及的诊断）
        local = _local_record_draft(args.dialogue_text, args.past_history)
        local["mode"] = "KNOWLEDGE_BASE"
        local["degradedReason"] = "未连接院内病历服务，改用内置知识库从对话中确定性提取（不推断未提及内容）"
        local["errorDetail"] = result
        return local
    return result


# ============================================================
# 内置知识库（降级模式）：从问诊对话确定性提取病历要素
# 仅提取对话中明确出现的内容；未提及诊断时如实标注"待医生明确诊断"，绝不臆造。
# ============================================================
_KB_DIAGNOSIS_HINTS = {
    "2型糖尿病": "2型糖尿病", "糖尿病": "2型糖尿病", "高血压": "高血压病",
    "冠心病": "冠心病", "哮喘": "支气管哮喘", "慢性肾病": "慢性肾脏病",
    "上呼吸道感染": "急性上呼吸道感染", "感冒": "急性上呼吸道感染",
    "胃肠炎": "急性胃肠炎", "头痛": "头痛待查",
}
_KB_MED_HINTS = ("二甲双胍", "阿莫西林", "布洛芬", "阿司匹林", "缬沙坦", "氨氯地平",
                 "阿奇霉素", "胰岛素", "辛伐他汀", "奥美拉唑")


def _local_record_draft(dialogue_text, past_history):
    """内置知识库病历草稿（本地回退，确定性提取）。"""
    dialogue = (dialogue_text or "").strip()
    diagnosis = ""
    for hint, standard in _KB_DIAGNOSIS_HINTS.items():
        if hint in dialogue:
            diagnosis = standard
            break
    medications = [name for name in _KB_MED_HINTS if name in dialogue]

    # 主诉：优先取"诉/主诉"之后到分隔符为止的片段，否则取前 40 字
    marker = max(dialogue.find("诉"), dialogue.find("主诉"))
    segment = dialogue[marker + 1:] if marker >= 0 and marker + 1 < len(dialogue) else dialogue
    for delimiter in ("，", "。", "；", ",", ";", "诊断", "开"):
        index = segment.find(delimiter)
        if index > 0:
            segment = segment[:index]
            break
    segment = segment.strip()
    chief = segment[:40] if len(segment) > 40 else (segment or "（对话为空，待医生补充主诉）")

    advice = ("处方：" + "、".join(medications) + "（用法用量由医生核定后开具）。"
              if medications else "建议完善相关检查后由医生确认治疗方案。")
    return {
        "chiefComplaint": chief,
        "presentIllness": "患者自述：" + dialogue + ("。" if dialogue else ""),
        "pastHistory": past_history or "既往史待医生补充。",
        "physicalExam": "体格检查待医生补充。",
        "diagnosis": diagnosis or "待医生明确诊断（知识库未从对话中识别出诊断关键词）。",
        "treatmentAdvice": advice,
        "degraded": True,
    }


# ============================================================
# action: history（查询历史病历）
# ============================================================
def action_history(args):
    """查询患者历史病历（走公开端点 /api/medical-record/list，按角色授权后客户端按 patientId 过滤）。

    说明：/internal/** 是服务间端点（需 X-Internal-Token），网关不对外路由；
    技能作为外部调用方必须走公开端点，避免"看起来能查、实际永远降级"的假动作。
    """
    if not args.patient_id:
        return {"error": "missing_param", "message": "patient-id is required"}

    result = _http_get("/api/medical-record/list")
    if isinstance(result, dict) and "error" in result:
        return {"records": [], "degraded": True, "errorDetail": result}
    records = result if isinstance(result, list) else ([result] if result else [])
    patient_id = _parse_int(args.patient_id)
    filtered = [
        r for r in records
        if isinstance(r, dict) and _parse_int(r.get("patientId")) == patient_id
    ]
    return {"records": filtered, "count": len(filtered), "degraded": False,
            "scopeNote": "按当前账号授权范围查询（医生=本人接诊病历）后按 patientId 过滤"}


# ============================================================
# action: save（保存病历）
# ============================================================
def action_save(args):
    """保存确认后的病历。"""
    if not (args.registration_id and args.chief_complaint and args.diagnosis):
        return {"error": "missing_param", "message": "registration-id, chief-complaint, diagnosis are required"}

    payload = {
        "registrationId": _parse_int(args.registration_id),
        "chiefComplaint": args.chief_complaint,
        "presentIllness": args.present_illness or "",
        "pastHistory": args.past_history or "",
        "physicalExam": args.physical_exam or "",
        "diagnosis": args.diagnosis,
        "treatmentAdvice": args.treatment_advice or "",
        "aiGenerated": args.ai_generated.lower() == "true" if args.ai_generated else True,
    }
    result = _http_post("/api/medical-record/save", payload)
    if isinstance(result, dict) and "error" in result:
        return {"success": False, "degraded": True, "errorDetail": result}
    return {"success": True, "medicalRecord": result}


def _parse_int(value):
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (ValueError, TypeError):
        return None


def build_parser():
    parser = argparse.ArgumentParser(description="智慧云脑病历草稿生成 Skill")
    parser.add_argument("--action", required=True, choices=["generate", "history", "save"],
                        help="执行的动作：generate=生成草稿, history=查历史病历, save=保存病历")
    parser.add_argument("--gateway-url", default=None, help="后端网关地址，覆盖环境变量")

    parser.add_argument("--registration-id", default=None, help="挂号ID")
    parser.add_argument("--dialogue-text", default=None, help="问诊对话文本")
    parser.add_argument("--department-code", default=None, help="科室代码")
    parser.add_argument("--patient-id", default=None, help="患者ID")

    parser.add_argument("--chief-complaint", default=None, help="主诉")
    parser.add_argument("--present-illness", default=None, help="现病史")
    parser.add_argument("--past-history", default=None, help="既往史")
    parser.add_argument("--physical-exam", default=None, help="查体")
    parser.add_argument("--diagnosis", default=None, help="诊断")
    parser.add_argument("--treatment-advice", default=None, help="处理建议")
    parser.add_argument("--ai-generated", default=None, help="是否AI生成 true/false")

    return parser


def main():
    global GATEWAY_URL
    parser = build_parser()
    args = parser.parse_args()

    if args.gateway_url:
        GATEWAY_URL = args.gateway_url
        os.environ["SCB_GATEWAY_URL"] = args.gateway_url

    if args.action == "generate":
        result = action_generate(args)
    elif args.action == "history":
        result = action_history(args)
    elif args.action == "save":
        result = action_save(args)
    else:
        result = {"error": "unknown_action", "action": args.action}

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
