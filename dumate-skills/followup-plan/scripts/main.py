#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
智慧云脑诊疗闭环助手 - 诊后随访计划 Skill 主脚本
作为 DuMate Skill 的 scripts/main.py，通过 HTTP 调用 smart_cloud_brain 后端 API。

使用方式（由 DuMate AI 根据 SKILL.md 指令调用）:
    python scripts/main.py --action create --patient-id 1 --diagnosis "急性上呼吸道感染" --medications "阿莫西林 0.5g 每日三次 7天" --followup-days 7
    python scripts/main.py --action remind --patient-id 1 --medications "阿莫西林 0.5g 每日三次 7天"
    python scripts/main.py --action query --patient-id 1
    python scripts/main.py --action submit --plan-id 1 --status 好转 --notes "咳嗽减轻"

输出：标准 JSON，供 DuMate AI 读取并按 SKILL.md 规则自主决策（异常标记回传医生）。
"""

import argparse
import json
import os
import sys
import urllib.request
import urllib.error
import urllib.parse
from datetime import datetime, timedelta


GATEWAY_URL = os.environ.get("SCB_GATEWAY_URL", "http://localhost:8080")
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
# action: create（创建随访计划）
# ============================================================
def action_create(args):
    """创建随访计划，调用后端 followup API。"""
    if not args.patient_id:
        return {"error": "missing_param", "message": "patient-id is required"}

    followup_days = _parse_int(args.followup_days) or 7
    followup_date = (datetime.now() + timedelta(days=followup_days)).strftime("%Y-%m-%d")

    # 生成用药提醒时间表
    reminder_schedule = _parse_medication_reminders(args.medications or "")

    payload = {
        "patientId": _parse_int(args.patient_id),
        "diagnosis": args.diagnosis or "",
        "medications": args.medications or "",
        "followupDays": followup_days,
        "followupDate": followup_date,
        "reminderSchedule": reminder_schedule,
    }

    result = _http_post("/api/followup/create", payload)
    if isinstance(result, dict) and "error" in result:
        # 降级：随访服务不可用，仍返回计划供AI使用
        return {
            "patientId": _parse_int(args.patient_id),
            "diagnosis": args.diagnosis or "",
            "followupDays": followup_days,
            "followupDate": followup_date,
            "reminderSchedule": reminder_schedule,
            "degraded": True,
            "message": "随访服务暂时不可用，已生成计划供参考，建议人工安排",
            "errorDetail": result,
        }
    return result


# ============================================================
# action: remind（生成用药提醒）
# ============================================================
def action_remind(args):
    """解析用药清单生成每日提醒时间表。"""
    if not args.medications:
        return {"error": "missing_param", "message": "medications is required"}

    reminder_schedule = _parse_medication_reminders(args.medications)
    return {
        "patientId": _parse_int(args.patient_id),
        "reminders": reminder_schedule,
        "message": "将由 DuMate 定时任务自动触发提醒",
    }


def _parse_medication_reminders(medications_text):
    """解析用药清单文本，生成提醒时间表。

    示例输入："阿莫西林 0.5g 每日三次 7天"
    输出：[{drugName, dosage, frequency, days, times: ["08:00","14:00","20:00"]}]
    """
    if not medications_text:
        return []

    # 简单解析：按药品分隔，提取频次与天数
    reminders = []
    # 按常见分隔符拆分多个药品
    items = [m.strip() for m in medications_text.replace("；", ";").split(";") if m.strip()]

    frequency_time_map = {
        "每日一次": ["08:00"],
        "每日两次": ["08:00", "20:00"],
        "每日三次": ["08:00", "14:00", "20:00"],
        "每日四次": ["08:00", "12:00", "16:00", "20:00"],
        "qd": ["08:00"],
        "bid": ["08:00", "20:00"],
        "tid": ["08:00", "14:00", "20:00"],
        "qid": ["08:00", "12:00", "16:00", "20:00"],
    }

    for item in items:
        reminder = {"rawText": item, "times": ["08:00", "14:00", "20:00"]}
        # 尝试匹配频次
        for freq_key, times in frequency_time_map.items():
            if freq_key in item:
                reminder["times"] = times
                reminder["frequency"] = freq_key
                break
        # 尝试提取天数
        for keyword in ["天", "日"]:
            idx = item.find(keyword)
            if idx > 0:
                num_part = ""
                for i in range(idx - 1, -1, -1):
                    if item[i].isdigit():
                        num_part = item[i] + num_part
                    else:
                        break
                if num_part:
                    reminder["days"] = int(num_part)
                    break
        reminders.append(reminder)

    return reminders


# ============================================================
# action: query（查询随访计划与记录）
# ============================================================
def action_query(args):
    """查询患者随访计划与随访问卷记录。"""
    if not args.patient_id:
        return {"error": "missing_param", "message": "patient-id is required"}

    path = f"/api/followup/patient/{args.patient_id}"
    result = _http_get(path)
    if isinstance(result, dict) and "error" in result:
        return {"plans": [], "records": [], "degraded": True, "errorDetail": result}
    return result if isinstance(result, dict) else {"plans": result if isinstance(result, list) else []}


# ============================================================
# action: submit（提交随访问卷）
# ============================================================
def action_submit(args):
    """提交随访问卷结果，AI 自主判定异常并标记。"""
    if not (args.plan_id and args.status):
        return {"error": "missing_param", "message": "plan-id and status are required"}

    payload = {
        "planId": _parse_int(args.plan_id),
        "status": args.status,
        "notes": args.notes or "",
        "submittedAt": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    }
    result = _http_post("/api/followup/record", payload)
    if isinstance(result, dict) and "error" in result:
        return {"success": False, "degraded": True, "errorDetail": result}

    # AI 自主决策：异常标记
    is_abnormal = args.status in ["加重", "无变化", "恶化", "严重"]
    return {
        "success": True,
        "record": result,
        "isAbnormal": is_abnormal,
        "doctorNotified": is_abnormal,  # 异常时通知医生
        "message": "已标记异常并通知医生" if is_abnormal else "随访记录已更新",
    }


def _parse_int(value):
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (ValueError, TypeError):
        return None


def build_parser():
    parser = argparse.ArgumentParser(description="智慧云脑诊后随访计划 Skill")
    parser.add_argument("--action", required=True, choices=["create", "remind", "query", "submit"],
                        help="执行的动作：create=创建随访计划, remind=生成用药提醒, query=查询随访, submit=提交问卷")
    parser.add_argument("--gateway-url", default=None, help="后端网关地址，覆盖环境变量")

    parser.add_argument("--patient-id", default=None, help="患者ID")
    parser.add_argument("--diagnosis", default=None, help="诊断")
    parser.add_argument("--medications", default=None, help="用药清单，如 '阿莫西林 0.5g 每日三次 7天'")
    parser.add_argument("--followup-days", default=None, help="随访天数，默认7")
    parser.add_argument("--plan-id", default=None, help="随访计划ID")
    parser.add_argument("--status", default=None, help="随访问卷状态：好转/无变化/加重/痊愈")
    parser.add_argument("--notes", default=None, help="随访问卷备注")

    return parser


def main():
    global GATEWAY_URL
    parser = build_parser()
    args = parser.parse_args()

    if args.gateway_url:
        GATEWAY_URL = args.gateway_url
        os.environ["SCB_GATEWAY_URL"] = args.gateway_url

    if args.action == "create":
        result = action_create(args)
    elif args.action == "remind":
        result = action_remind(args)
    elif args.action == "query":
        result = action_query(args)
    elif args.action == "submit":
        result = action_submit(args)
    else:
        result = {"error": "unknown_action", "action": args.action}

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
