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


GATEWAY_URL = os.environ.get("SCB_GATEWAY_URL", "http://localhost:8080")
TIMEOUT = 20  # 病历生成耗时较长


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
        return {
            "chiefComplaint": "",
            "presentIllness": "",
            "pastHistory": "",
            "physicalExam": "",
            "diagnosis": "",
            "treatmentAdvice": "",
            "degraded": True,
            "errorDetail": result,
            "message": "病历生成服务暂时不可用，建议人工编写",
        }
    return result


# ============================================================
# action: history（查询历史病历）
# ============================================================
def action_history(args):
    """查询患者历史病历。"""
    if not args.patient_id:
        return {"error": "missing_param", "message": "patient-id is required"}

    path = f"/internal/medical-records/patient/{args.patient_id}"
    result = _http_get(path)
    if isinstance(result, dict) and "error" in result:
        return {"records": [], "degraded": True, "errorDetail": result}
    return {"records": result if isinstance(result, list) else [result] if result else []}


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
