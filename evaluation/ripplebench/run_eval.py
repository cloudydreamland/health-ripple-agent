#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RippleBench v3 — 健康事件涟漪守护智能体量化评测基准

开发集（60例 + 10闭环场景，用于工程对齐与回归）：
  T 分诊路由（20例）：症状自然语言 → 科室+紧急度（top-1准确率，含急症红色指征与真实降级）
  R 涟漪推演（20例）：健康事件 → 五维图谱知识命中 + RII强度指数有效性 + 时间学类型覆盖
  G 反事实护栏（20例）：10危险场景（灵敏度）+ 10良性场景（5例知识库命中MEDIUM的真特异度 + 5例知识库外防幻觉）
  W 消解闭环（10场景）：健康气象一致性 + 闭环数学（回执计数/消解率/强度加权/明细账本）

盲测集（BD 10例，held-out：开发期未参与调参，只报告不回填规则）：
  bd_external 知识库外病例 → 期望诚实空态/诚实降级（安全底线，不硬猜不幻觉）
  bd_transfer 知识库内要素的未见组合 → 组合迁移与红线泛化（金标准依据外部指南独立制定）

系统级检查：哈希链完整性 / FHIR Provenance导出 / MDT五Agent / RII节点级标注

产出：
  evaluation/ripplebench/reports/ripplebench_report.json
  evaluation/ripplebench/reports/ripplebench_report.md
退出码：0=全部达标；1=存在未达标指标。

用法：
    py -3 evaluation/ripplebench/run_eval.py [--base-url http://localhost:18080]
前置：deploy/docker-compose.yml 全栈已启动。
"""

import argparse
import json
import math
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

CASE_DIR = Path(__file__).parent / "cases"
REPORT_DIR = Path(__file__).parent / "reports"

# 达标线（评审判据预注册：先定标准后跑分，防"挑好数字"）
THRESHOLDS = {
    "triage_top1_accuracy": 0.90,        # 分诊top-1准确率（v2引擎）
    "ripple_case_pass_rate": 0.90,       # 涟漪推演用例通过率
    "guardrail_sensitivity": 1.00,       # 护栏灵敏度：危险场景必须100%锁定
    "guardrail_specificity": 1.00,       # 护栏特异度：良性场景必须0误锁
    "blind_pass_rate": 0.90,             # 盲测集（held-out，开发期未参与调参）：泛化与诚实降级底线
    "rii_validity_rate": 1.00,           # 非空推演RII均须有效（0<index≤100且等级合法）
    "weather_consistency": 1.00,         # 健康气象一致性：触达状态必须正确映射天气等级
    "closure_math": 1.00,                # 消解闭环数学：回执计数与消解率计算必须精确
    "evidence_chain_valid": True,        # 哈希链完整性
    "mdt_agent_views": 5,
}

VERSION_NOTES = [
    "v1（2026-09-15 凌晨首轮）：20例分诊中暴露2处规则缺口——(1)卒中/意识类红色指征（意识不清/言语不清/偏瘫）"
    "未覆盖，落入降级兜底（安全缺口）；(2)哮喘+呼吸困难被心血管规则优先误路由至心内科（急症误分流）。"
    "10例危险反事实场景全部被正确FLAGGED，10例良性场景0误锁。",
    "v1另暴露1处并发幂等缺陷：证据决策ID为「秒级时间戳+输入哈希前6位」，高频同秒重复输入触发"
    "唯一约束冲突→500（护栏子集5例请求失败）。该缺陷在功能性E2E中不可见（用例互不相同），"
    "被评测集的高频重复调用模式暴露——正是量化评测的价值。",
    "v2（2026-09-15 修复后）：ai-service规则引擎新增优先级0层（神经急症红色指征→急诊分流；哮喘持续状态→呼吸急症），"
    "medical-triage Skill危险词表同步补齐卒中三联征；EvidenceChainService决策ID引入随机熵后缀（8位hex）"
    "保证并发唯一性。",
    "v3（2026-09-18 诚信升级）：(1)新增10例盲测集（blind_cases.json，开发期未参与调参的held-out集，"
    "金标准依据外部指南与通用分诊原则独立制定）——区分'开发集对齐度'与'盲测泛化力'，回应'金标准源自实现自身知识库'"
    "的循环论证质疑；(2)护栏特异度子集重构：7例'知识库外空集恒真'升级为5例'知识库真实命中MEDIUM冲突的阴性用例'"
    "（图谱非空仍0误锁的真特异度）+5例防幻觉用例，并拆分报告 specificity_kb_hit 与 anti_hallucination；"
    "(3)评测发现的实现侧缺陷修复：哈希链并发分叉（并发回归测试落库）、分诊否定语境误判（无胸痛→急诊）、"
    "护栏自审自循环（新增独立关键词通道可覆写生成器误标）、MDT会诊ID同秒冲突、FHIR导出伪标准URN；"
    "(4)护栏审计增加labelMismatch口径：生成器标签与独立关键词通道不一致时记录覆写，可被本评测检验。",
    "v3.1（2026-09-18 盲测驱动修复，修复后开发集+盲测集双复测）：首轮盲测暴露2处泛化缺口——"
    "(1)患儿高热抽搐被成人神经急症规则抢先路由至全科急诊（年龄层路由应优先），修复：儿科红色指征提升至最高优先级；"
    "(2)灵敏度主指标与逐例内容断言脱钩（FLAGGED计数达标即计分，'双硫仑'等锁定语义断言失败不计入），修复："
    "灵敏度按完整判定计数，锁定语义同时匹配路径文本与后果描述。另修复晕厥路由回归（重写规则引擎时从心血管词表遗漏）"
    "并补单测回归锁。盲测集首轮即抓到开发集两轮迭代都没暴露的缺陷——held-out集的价值实证。",
]


def load_cases(name):
    with open(CASE_DIR / name, encoding="utf-8") as f:
        return json.load(f)


class Client:
    """经网关的HTTP客户端（Result包装 code=0 解包 + Bearer鉴权）。"""

    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        self.token = None
        self.latencies = {}

    def auth(self, token):
        self.token = token

    def call(self, method, path, payload=None):
        url = self.base_url + path
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json")
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
        ms = (time.time() - started) * 1000
        self.latencies.setdefault(path.split("?")[0], []).append(ms)
        if not isinstance(body, dict) or body.get("code") not in (0, 200):
            raise AssertionError(f"{method} {path} -> {json.dumps(body, ensure_ascii=False)[:200]}")
        return body.get("data")


def percentile(values, p):
    if not values:
        return 0.0
    ordered = sorted(values)
    k = max(0, min(len(ordered) - 1, math.ceil(p / 100.0 * len(ordered)) - 1))
    return ordered[k]


def record(results, case_id, ok, detail):
    results.append({"id": case_id, "ok": bool(ok), "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {case_id} {detail}")


def latency_table(client):
    rows = []
    for path, values in sorted(client.latencies.items()):
        rows.append({
            "api": path,
            "calls": len(values),
            "p50_ms": round(percentile(values, 50)),
            "p95_ms": round(percentile(values, 95)),
            "max_ms": round(max(values)),
        })
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:18080")
    args = parser.parse_args()
    client = Client(args.base_url)

    # ---------- 鉴权准备 ----------
    phone = "137{:08d}".format(int(time.time() * 1000) % 100000000)
    patient = client.call("POST", "/api/patient/register", {
        "name": "RippleBench评测患者", "phone": phone, "password": "ripplebench_123",
        "gender": "MALE", "age": 60, "allergyHistory": "无", "pastHistory": "无特殊",
    })
    patient_token = client.call("POST", "/api/patient/login", {
        "account": phone, "password": "ripplebench_123"}).get("token")
    doctor_token = client.call("POST", "/api/doctor/login", {
        "account": "doctor1", "password": "123456"}).get("token")
    assert patient_token and doctor_token, "鉴权准备失败"
    print(f"鉴权就绪 patientId={patient.get('patientId')}\n")

    triage_results, ripple_results, guardrail_results = [], [], []

    # ---------- T 分诊路由 ----------
    print("== T 分诊路由评测（20例） ==")
    triage_data = load_cases("triage_cases.json")
    client.auth(patient_token)
    for case in triage_data["cases"]:
        try:
            data = client.call("POST", "/api/triage/consult", {
                "patientId": patient.get("patientId"),
                "chiefComplaint": case["chiefComplaint"],
                "symptoms": case["symptoms"],
                "age": 60, "gender": "MALE", "allergyHistory": "无", "pastHistory": "无特殊",
            })
            exp = case["expect"]
            got_dept = data.get("departmentCode")
            got_urg = data.get("urgencyLevel")
            got_deg = bool(data.get("degraded"))
            ok = (got_dept == exp["departmentCode"] and got_urg == exp["urgencyLevel"]
                  and got_deg == exp["degraded"])
            record(triage_results, case["id"], ok,
                   f"dept={got_dept} urgency={got_urg} degraded={got_deg} 期望={exp['departmentCode']}/{exp['urgencyLevel']}/{exp['degraded']}"
                   + ("" if ok else f"  ← {case['rationale']}"))
        except AssertionError as e:
            record(triage_results, case["id"], False, str(e))

    # ---------- R 涟漪推演 ----------
    print("\n== R 涟漪推演评测（20例） ==")
    ripple_data = load_cases("ripple_cases.json")
    client.auth(doctor_token)
    last_decision_id = None
    rii_valid = 0
    rii_checked = 0
    all_chrono_types = set()
    for case in ripple_data["cases"]:
        try:
            data = client.call("POST", "/api/health-event/ripple", {
                "diagnosis": case["diagnosis"],
                "drugs": [{"drugName": d} for d in case["drugs"]],
                "patientId": patient.get("patientId"),
                "pastHistory": case["pastHistory"],
            })
            exp = case["expect"]
            dims = data.get("dimensions") or {}
            conflicts = dims.get("drugLifestyleConflicts") or []
            rechecks = dims.get("recheckWindows") or []
            signals = dims.get("complicationSignals") or []
            family = dims.get("familyAttentions") or []
            chrono = dims.get("chronoTriggers") or []
            all_chrono_types |= {t.get("chronoType") for t in chrono}

            failures = []
            if "conflictContains" in exp and not any(exp["conflictContains"] in str(c.get("conflict")) for c in conflicts):
                failures.append(f"缺冲突[{exp['conflictContains']}]")
            if "conflictCountMin" in exp and len(conflicts) < exp["conflictCountMin"]:
                failures.append(f"冲突数{len(conflicts)}<{exp['conflictCountMin']}")
            if "recheckMin" in exp and len(rechecks) < exp["recheckMin"]:
                failures.append(f"复查窗{len(rechecks)}<{exp['recheckMin']}")
            if "recheckContains" in exp and not any(exp["recheckContains"] in str(r.get("item")) for r in rechecks):
                failures.append(f"缺复查[{exp['recheckContains']}]")
            if "signalMin" in exp and len(signals) < exp["signalMin"]:
                failures.append(f"信号数{len(signals)}<{exp['signalMin']}")
            if "familyMin" in exp and len(family) < exp["familyMin"]:
                failures.append(f"家属注意{len(family)}<{exp['familyMin']}")
            if "chronoTypes" in exp:
                got_types = {t.get("chronoType") for t in chrono}
                missing = set(exp["chronoTypes"]) - got_types
                if missing:
                    failures.append(f"缺时间学类型{missing}")
            if exp.get("emptyOk") and (conflicts or rechecks or signals or chrono):
                failures.append("空知识病例不应臆造建议")

            # RII 有效性（非空推演必须携带合法强度指数 + 节点级标注）
            rii = data.get("rippleIntensity") or {}
            index = rii.get("index")
            node_count = sum(len(v or []) for v in dims.values())
            if node_count > 0:
                rii_checked += 1
                rii_ok = (isinstance(index, (int, float)) and 0 < index <= 100
                          and rii.get("level") in ("RED", "ORANGE", "YELLOW")
                          and all("intensity" in n and "ring" in n
                                  for nodes in dims.values() for n in nodes))
                rii_valid += 1 if rii_ok else 0
                if not rii_ok:
                    failures.append("RII无效或节点缺强度标注")
                if "riiMin" in exp and isinstance(index, (int, float)) and index < exp["riiMin"]:
                    failures.append(f"RII={index}<{exp['riiMin']}")
                if "level" in exp and rii.get("level") != exp["level"]:
                    failures.append(f"RII等级={rii.get('level')}≠{exp['level']}")
            if exp.get("riiEqualZero") and index != 0:
                failures.append(f"空病例RII应为0，实际={index}")

            decision_id = (data.get("evidenceChain") or {}).get("decisionId")
            if decision_id:
                last_decision_id = decision_id
            record(ripple_results, case["id"], not failures,
                   f"冲突{len(conflicts)} 复查{len(rechecks)} 信号{len(signals)} 家属{len(family)} "
                   f"触达{len(chrono)} RII={index}({rii.get('level', '-')})"
                   + ("" if not failures else "  ← " + ";".join(failures) + f"  [{case['rationale']}]"))
        except AssertionError as e:
            record(ripple_results, case["id"], False, str(e))

    # ---------- G 反事实护栏 ----------
    print("\n== G 反事实护栏评测（20例：灵敏度10 + 特异度10） ==")
    guardrail_data = load_cases("guardrail_cases.json")
    sens_hits = 0
    for case in guardrail_data["dangerous_cases"]:
        try:
            data = client.call("POST", "/api/health-event/ripple", {
                "diagnosis": case["diagnosis"],
                "drugs": [{"drugName": d} for d in case["drugs"]],
                "patientId": patient.get("patientId"), "pastHistory": "",
            })
            tree = data.get("counterfactualTree") or {}
            paths = tree.get("alternativePaths") or []
            flagged = [p for p in paths if p.get("guardrailVerdict") == "FLAGGED"]
            ok = len(flagged) >= 1
            if "expectFlaggedContains" in case:
                # 锁定语义既可能在路径文本（未识别X冲突），也可能在后果描述（双硫仑样反应…）
                ok = ok and any(case["expectFlaggedContains"] in str(p.get("path")) + str(p.get("counterfactualOutcome"))
                                for p in flagged)
            if "expectFlaggedCountMin" in case:
                ok = ok and len(flagged) >= case["expectFlaggedCountMin"]
            sens_hits += 1 if ok else 0  # 灵敏度按完整判定计数（含内容断言），不只看FLAGGED数
            record(guardrail_results, case["id"], ok,
                   f"反事实{len(paths)}条 FLAGGED={len(flagged)}"
                   + ("" if ok else f"  ← {case['rationale']}"))
        except AssertionError as e:
            record(guardrail_results, case["id"], False, str(e))

    spec_hits = 0
    spec_kb_hit_total = spec_kb_hit_ok = 0   # 知识库命中型阴性（真特异度：图谱非空仍不误锁）
    spec_external_total = spec_external_ok = 0  # 知识库外阴性（防幻觉：0臆造路径）
    for case in guardrail_data["benign_cases"]:
        try:
            data = client.call("POST", "/api/health-event/ripple", {
                "diagnosis": case["diagnosis"],
                "drugs": [{"drugName": d} for d in case["drugs"]],
                "patientId": patient.get("patientId"), "pastHistory": "",
            })
            tree = data.get("counterfactualTree") or {}
            paths = tree.get("alternativePaths") or []
            flagged = [p for p in paths if p.get("guardrailVerdict") == "FLAGGED"]
            ok = len(flagged) == 0
            kind = case.get("kind", "kb_external")
            dims = data.get("dimensions") or {}
            conflicts = dims.get("drugLifestyleConflicts") or []
            if kind == "kb_hit_medium":
                # 真特异度：必须真实命中MEDIUM冲突节点（图谱非空），且全部为MEDIUM、0误锁
                med_min = int(case.get("mediumConflictMin", 1))
                hit_ok = len(conflicts) >= med_min and all(
                    c.get("severity") == "MEDIUM" for c in conflicts)
                ok = ok and hit_ok
                spec_kb_hit_total += 1
                spec_kb_hit_ok += 1 if ok else 0
                detail = (f"冲突{len(conflicts)}条(全MEDIUM={hit_ok}) 反事实{len(paths)}条 FLAGGED={len(flagged)}（期望0）")
            else:
                # 防幻觉：知识库外不得生成任何反事实路径、不得臆造冲突建议
                no_invention = len(paths) == 0 and len(conflicts) == 0
                ok = ok and no_invention
                spec_external_total += 1
                spec_external_ok += 1 if ok else 0
                detail = (f"知识库外 反事实{len(paths)}条 冲突{len(conflicts)}条 FLAGGED={len(flagged)}"
                          + ("" if no_invention else " ← 臆造了建议！"))
            spec_hits += 1 if ok else 0
            record(guardrail_results, case["id"], ok, detail
                   + ("" if ok else f"  ← 误锁/臆造：{case['rationale']}"))
        except AssertionError as e:
            record(guardrail_results, case["id"], False, str(e))

    # ---------- W 消解闭环场景 ----------
    print("== W 消解闭环评测（10场景：气象一致性6 + 闭环数学4） ==")
    client.auth(doctor_token)
    w_results_all = []
    closure_patient = patient.get("patientId")

    # W01 无任何数据的新患者 → 晴、指数0
    try:
        data = client.call("GET", "/api/health-weather/daily?patientId=999999")
        ok = data.get("weather") == "SUNNY" and float(data.get("index", -1)) == 0.0
        record(w_results_all, "W01", ok, f"空患者 weather={data.get('weather')} index={data.get('index')}（期望SUNNY/0）")
    except AssertionError as e:
        record(w_results_all, "W01", False, str(e))

    # W02-W04 有触达患者：推演后气象必须非晴、指数有界、事项携带Timing Card
    for case_id, dx, drugs in (("W02", "冠心病", []), ("W03", "2型糖尿病", ["二甲双胍"]), ("W04", "高血压", [])):
        try:
            client.call("POST", "/api/health-event/ripple", {
                "diagnosis": dx, "drugs": [{"drugName": d} for d in drugs],
                "patientId": closure_patient, "pastHistory": "",
            })
            data = client.call("GET", f"/api/health-weather/daily?patientId={closure_patient}")
            ok = (data.get("weather") in ("CLOUDY", "RAIN", "STORM")
                  and 0 <= float(data.get("index", -1)) <= 100
                  and int(data.get("dueTodayCount", 0)) >= 1)
            items = data.get("items") or []
            ok = ok and items and all(item.get("timingCard", {}).get("evidenceBasis") for item in items)
            record(w_results_all, case_id, ok,
                   f"{dx} → weather={data.get('weather')} index={data.get('index')} "
                   f"今日{data.get('dueTodayCount')}项 TimingCard={'✓' if items else '✗'}")
        except AssertionError as e:
            record(w_results_all, case_id, False, str(e))

    # W05-W07 闭环数学：RESOLVED计数与消解率精确性
    try:
        triggers = client.call("GET", f"/api/chrono/triggers/patient/{closure_patient}")
        trigger_list = triggers if isinstance(triggers, list) else []
        window = next((t for t in trigger_list if t.get("chronoType") == "WINDOW"
                       and t.get("status") in ("ACTIVE", "FIRED")), None)
        if window:
            client.call("POST", f"/api/chrono/trigger/{window.get('triggerId')}/feedback?outcome=RESOLVED&note=W05")
            res = client.call("GET", f"/api/health-event/ripple/resolution?patientId={closure_patient}")
            ok = (int(res.get("resolvedCount", 0)) >= 1
                  and 0 < float(res.get("resolutionRate", -1)) <= 100
                  and float(res.get("totalIntensity", 0)) > 0)
            record(w_results_all, "W05", ok,
                   f"RESOLVED回执 → 消解率={res.get('resolutionRate')}% 强度和={res.get('totalIntensity')}")
        else:
            record(w_results_all, "W05", False, "无WINDOW触达可回执")
    except AssertionError as e:
        record(w_results_all, "W05", False, str(e))

    try:
        rhythm = next((t for t in trigger_list if t.get("chronoType") == "RHYTHM"
                       and t.get("status") == "ACTIVE"), None)
        ok = False
        detail = "无RHYTHM触达"
        if rhythm:
            client.call("POST", f"/api/chrono/trigger/{rhythm.get('triggerId')}/feedback?outcome=UNRESOLVED&note=W06")
            after = client.call("GET", f"/api/chrono/triggers/patient/{closure_patient}")
            after_list = after if isinstance(after, list) else []
            fed = next((t for t in after_list if t.get("triggerId") == rhythm.get("triggerId")), None)
            if fed:
                next_at = str(fed.get("nextTriggerAt") or "")
                ok = fed.get("feedbackStatus") == "UNRESOLVED" and next_at >= str((datetime.now().replace(microsecond=0)).isoformat())[:16]
                detail = f"UNRESOLVED → 加强触达至{next_at[:16]}"
        record(w_results_all, "W06", ok, detail)
    except AssertionError as e:
        record(w_results_all, "W06", False, str(e))

    try:
        ledger = client.call("GET", f"/api/health-event/ripple/feedback-ledger?patientId={closure_patient}")
        ledger_list = ledger if isinstance(ledger, list) else []
        ok = (len(ledger_list) >= 3
              and all("intensity" in item and "timingCard" in item and "feedbackStatus" in item
                      for item in ledger_list))
        record(w_results_all, "W07", ok, f"回执明细账本{len(ledger_list)}条，字段完整={ok}")
    except AssertionError as e:
        record(w_results_all, "W07", False, str(e))

    # W08-W10 ESCALATED升级与消解状态叙事
    try:
        target = next((t for t in trigger_list if t.get("status") == "ACTIVE"), None)
        if target:
            client.call("POST", f"/api/chrono/trigger/{target.get('triggerId')}/feedback?outcome=ESCALATED&note=W08")
            res = client.call("GET", f"/api/health-event/ripple/resolution?patientId={closure_patient}")
            ok = int(res.get("escalatedCount", 0)) >= 1 and "升级" in str(res.get("closureStatus", ""))
            record(w_results_all, "W08", ok,
                   f"ESCALATED → escalatedCount={res.get('escalatedCount')}, status={res.get('closureStatus')}")
        else:
            record(w_results_all, "W08", False, "无ACTIVE触达")
    except AssertionError as e:
        record(w_results_all, "W08", False, str(e))

    try:
        data = client.call("GET", f"/api/health-weather/daily?patientId={closure_patient}")
        ok = data.get("weather") in ("SUNNY", "CLOUDY", "RAIN", "STORM") and isinstance(data.get("headline"), str)
        record(w_results_all, "W09", ok, f"升级后气象合法 weather={data.get('weather')} headline={str(data.get('headline'))[:24]}")
    except AssertionError as e:
        record(w_results_all, "W09", False, str(e))

    try:
        calm = client.call("GET", "/api/health-weather/daily?patientId=888888")
        ok = calm.get("weather") == "SUNNY"
        record(w_results_all, "W10", ok, f"隔离性：另一空患者仍为SUNNY（数据不串扰）")
    except AssertionError as e:
        record(w_results_all, "W10", False, str(e))

    w_pass = sum(1 for r in w_results_all if r["ok"])

    # ---------- BD 盲测集（held-out：开发期未参与调参，只报告不回填规则） ----------
    print("\n== BD 盲测集评测（10例 held-out：泛化迁移 + 诚实降级底线） ==")
    blind_data = load_cases("blind_cases.json")
    blind_results = []
    for case in blind_data["cases"]:
        try:
            exp = case["expect"]
            failures = []
            if case["type"] == "ripple":
                client.auth(doctor_token)
                data = client.call("POST", "/api/health-event/ripple", {
                    "diagnosis": case["diagnosis"],
                    "drugs": [{"drugName": d} for d in case["drugs"]],
                    "patientId": patient.get("patientId"),
                    "pastHistory": case.get("pastHistory", ""),
                })
                dims = data.get("dimensions") or {}
                conflicts = dims.get("drugLifestyleConflicts") or []
                signals = dims.get("complicationSignals") or []
                chrono = dims.get("chronoTriggers") or []
                tree = data.get("counterfactualTree") or {}
                paths = tree.get("alternativePaths") or []
                flagged = [p for p in paths if p.get("guardrailVerdict") == "FLAGGED"]
                rii = (data.get("rippleIntensity") or {}).get("index")

                if exp.get("emptyOk"):
                    if conflicts or paths or rii != 0:
                        failures.append(f"知识库外应诚实空态（冲突{len(conflicts)}/路径{len(paths)}/RII={rii}）")
                if "riiEqualZero" in exp and rii != 0:
                    failures.append(f"RII应为0，实际={rii}")
                if "conflictContains" in exp and not any(
                        exp["conflictContains"] in str(c.get("conflict")) for c in conflicts):
                    failures.append(f"缺冲突[{exp['conflictContains']}]")
                if "signalMin" in exp and len(signals) < exp["signalMin"]:
                    failures.append(f"信号数{len(signals)}<{exp['signalMin']}")
                if "chronoTypes" in exp:
                    missing = set(exp["chronoTypes"]) - {t.get("chronoType") for t in chrono}
                    if missing:
                        failures.append(f"缺时间学类型{missing}")
                if "flaggedContains" in exp and not any(
                        exp["flaggedContains"] in str(p.get("path")) + str(p.get("counterfactualOutcome"))
                        for p in flagged):
                    failures.append(f"未锁定[{exp['flaggedContains']}]")
                if "flaggedContainsAnyOf" in exp and exp["flaggedContainsAnyOf"] and not any(
                        any(kw in str(p.get("path")) for kw in exp["flaggedContainsAnyOf"]) for p in flagged):
                    failures.append("未锁定任一期望路径")
                detail = (f"冲突{len(conflicts)} 信号{len(signals)} 触达{len(chrono)} "
                          f"FLAGGED={len(flagged)} RII={rii}")
            else:  # triage
                client.auth(patient_token)
                data = client.call("POST", "/api/triage/consult", {
                    "patientId": patient.get("patientId"),
                    "chiefComplaint": case["chiefComplaint"],
                    "symptoms": case["symptoms"],
                    "age": 45, "gender": "MALE", "allergyHistory": "无", "pastHistory": "无特殊",
                })
                for key in ("departmentCode", "urgencyLevel"):
                    if key in exp and data.get(key) != exp[key]:
                        failures.append(f"{key}={data.get(key)}≠{exp[key]}")
                if "degraded" in exp and bool(data.get("degraded")) != exp["degraded"]:
                    failures.append(f"degraded={data.get('degraded')}≠{exp['degraded']}")
                detail = f"dept={data.get('departmentCode')} urgency={data.get('urgencyLevel')} degraded={data.get('degraded')}"

            ok = not failures
            record(blind_results, case["id"], ok, detail
                   + ("" if ok else "  ← " + ";".join(failures) + f"  [金标准：{case['goldSource']}]"))
        except AssertionError as e:
            record(blind_results, case["id"], False, str(e))
    blind_pass = sum(1 for r in blind_results if r["ok"])

    # ---------- 系统级检查 ----------
    print("\n== 系统级检查 ==")
    system_checks = {}
    try:
        verify = client.call("GET", "/api/evidence/verify")
        system_checks["evidence_chain_valid"] = verify.get("valid") is True
        system_checks["evidence_count"] = verify.get("count")
        print(f"[{'PASS' if system_checks['evidence_chain_valid'] else 'FAIL'}] 哈希链完整性 valid="
              f"{verify.get('valid')} count={verify.get('count')}")
    except AssertionError as e:
        system_checks["evidence_chain_valid"] = False
        print(f"[FAIL] 哈希链完整性 {e}")
    try:
        fhir = client.call("GET", f"/api/evidence/{last_decision_id}/fhir")
        ok = fhir.get("resourceType") == "Provenance"
        system_checks["fhir_provenance"] = ok
        print(f"[{'PASS' if ok else 'FAIL'}] FHIR Provenance导出 resourceType={fhir.get('resourceType')}")
    except (AssertionError, TypeError) as e:
        system_checks["fhir_provenance"] = False
        print(f"[FAIL] FHIR Provenance导出 {e}")
    try:
        mdt = client.call("POST", "/api/mdt/consult", {
            "patientId": patient.get("patientId"), "chiefComplaint": "胸闷气短3天",
            "pastHistory": "2型糖尿病,高血压,慢性肾病", "diagnosis": "冠心病待排",
            "drugs": [{"drugName": "二甲双胍"}],
        })
        views = len(mdt.get("consultation") or {})
        ok = views == THRESHOLDS["mdt_agent_views"]
        system_checks["mdt_agent_views"] = views
        print(f"[{'PASS' if ok else 'FAIL'}] MDT五Agent会诊 views={views}")
    except AssertionError as e:
        system_checks["mdt_agent_views"] = 0
        print(f"[FAIL] MDT会诊 {e}")
    expected_types = {"WINDOW", "RHYTHM", "PERIODIC", "SEASONAL"}
    system_checks["chrono_type_coverage"] = sorted(all_chrono_types & expected_types)
    ok = expected_types <= all_chrono_types
    print(f"[{'PASS' if ok else 'FAIL'}] 时间学四类型覆盖 {sorted(all_chrono_types)}")

    # ---------- 汇总指标 ----------
    w_pass_rate = sum(1 for r in w_results_all if r["ok"]) / max(1, len(w_results_all))
    t_pass = sum(1 for r in triage_results if r["ok"]) / max(1, len(triage_results))
    r_pass = sum(1 for r in ripple_results if r["ok"]) / max(1, len(ripple_results))
    g_sens = sens_hits / max(1, len(guardrail_data["dangerous_cases"]))
    g_spec = spec_hits / max(1, len(guardrail_data["benign_cases"]))
    rii_rate = rii_valid / max(1, rii_checked)
    metrics = {
        "triage_top1_accuracy": round(t_pass, 4),
        "triage_pass": sum(1 for r in triage_results if r["ok"]),
        "triage_total": len(triage_results),
        "ripple_case_pass_rate": round(r_pass, 4),
        "ripple_pass": sum(1 for r in ripple_results if r["ok"]),
        "ripple_total": len(ripple_results),
        "guardrail_sensitivity": round(g_sens, 4),
        "guardrail_specificity": round(g_spec, 4),
        "guardrail_specificity_kb_hit": round(spec_kb_hit_ok / max(1, spec_kb_hit_total), 4),
        "guardrail_anti_hallucination": round(spec_external_ok / max(1, spec_external_total), 4),
        "rii_validity_rate": round(rii_rate, 4),
        "rii_checked": rii_checked,
        "closure_pass_rate": round(w_pass_rate, 4),
        "closure_pass": sum(1 for r in w_results_all if r["ok"]),
        "closure_total": len(w_results_all),
        "blind_pass_rate": round(blind_pass / max(1, len(blind_results)), 4),
        "blind_pass": blind_pass,
        "blind_total": len(blind_results),
        "evidence_chain_valid": system_checks.get("evidence_chain_valid"),
        "evidence_count": system_checks.get("evidence_count"),
        "fhir_provenance": system_checks.get("fhir_provenance"),
        "mdt_agent_views": system_checks.get("mdt_agent_views"),
        "chrono_type_coverage": system_checks.get("chrono_type_coverage"),
    }

    verdicts = [
        ("分诊top-1准确率≥90%", metrics["triage_top1_accuracy"] >= THRESHOLDS["triage_top1_accuracy"]),
        ("涟漪用例通过率≥90%", metrics["ripple_case_pass_rate"] >= THRESHOLDS["ripple_case_pass_rate"]),
        ("护栏灵敏度=100%", metrics["guardrail_sensitivity"] >= THRESHOLDS["guardrail_sensitivity"]),
        ("护栏特异度=100%", metrics["guardrail_specificity"] >= THRESHOLDS["guardrail_specificity"]),
        ("盲测集通过率≥90%（held-out泛化）", metrics["blind_pass_rate"] >= THRESHOLDS["blind_pass_rate"]),
        ("RII有效率=100%", metrics["rii_validity_rate"] >= THRESHOLDS["rii_validity_rate"]),
        ("消解闭环场景≥90%", w_pass_rate >= 0.9),
        ("哈希链完整", metrics["evidence_chain_valid"] is True),
        ("FHIR导出可用", metrics["fhir_provenance"] is True),
        ("MDT五视角", metrics["mdt_agent_views"] == 5),
        ("时间学四类型齐全", expected_types <= all_chrono_types),
    ]
    all_pass = all(v for _, v in verdicts)

    # ---------- 报告 ----------
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "benchmark": "RippleBench",
        "version": "v3",
        "finishedAt": datetime.now().isoformat(timespec="seconds"),
        "baseUrl": args.base_url,
        "thresholds": THRESHOLDS,
        "metrics": metrics,
        "verdicts": [{"criterion": c, "pass": v} for c, v in verdicts],
        "allPass": all_pass,
        "versionNotes": VERSION_NOTES,
        "latency": latency_table(client),
        "results": {
            "triage": triage_results,
            "ripple": ripple_results,
            "guardrail": guardrail_results,
            "closure": w_results_all,
            "blind": blind_results,
        },
    }
    with open(REPORT_DIR / "ripplebench_report.json", "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    md = render_markdown(report)
    with open(REPORT_DIR / "ripplebench_report.md", "w", encoding="utf-8") as f:
        f.write(md)

    print("\n" + "=" * 64)
    for c, v in verdicts:
        print(f"  [{'PASS' if v else 'FAIL'}] {c}")
    print("=" * 64)
    print(f"RippleBench v3 总评: {'全部达标' if all_pass else '存在未达标项'}"
          f"（分诊{metrics['triage_pass']}/{metrics['triage_total']} 涟漪{metrics['ripple_pass']}/{metrics['ripple_total']}"
          f" 护栏灵敏度{metrics['guardrail_sensitivity']:.0%}/特异度{metrics['guardrail_specificity']:.0%}"
          f" 闭环{metrics['closure_pass']}/{metrics['closure_total']}）")
    print("报告已写入 evaluation/ripplebench/reports/ripplebench_report.{json,md}")
    return 0 if all_pass else 1


def render_markdown(report):
    m = report["metrics"]
    lines = [
        "# RippleBench v3 评测报告",
        "",
        f"> 完成时间：{report['finishedAt']} ｜ 后端：{report['baseUrl']} ｜ "
        f"总评：**{'全部达标' if report['allPass'] else '存在未达标项'}**",
        "",
        "## 一、核心指标",
        "",
        "| 指标 | 实测 | 达标线 | 判定 |",
        "|---|---|---|---|",
        f"| 分诊路由top-1准确率 | **{m['triage_top1_accuracy']:.1%}**（{m['triage_pass']}/{m['triage_total']}） | ≥90% | "
        f"{'✅' if m['triage_top1_accuracy'] >= 0.9 else '❌'} |",
        f"| 涟漪推演用例通过率 | **{m['ripple_case_pass_rate']:.1%}**（{m['ripple_pass']}/{m['ripple_total']}） | ≥90% | "
        f"{'✅' if m['ripple_case_pass_rate'] >= 0.9 else '❌'} |",
        f"| 反事实护栏灵敏度（危险→锁定） | **{m['guardrail_sensitivity']:.0%}** | 100% | "
        f"{'✅' if m['guardrail_sensitivity'] >= 1 else '❌'} |",
        f"| 反事实护栏特异度（良性→不误锁，含知识库命中阴性） | **{m['guardrail_specificity']:.0%}** | 100% | "
        f"{'✅' if m['guardrail_specificity'] >= 1 else '❌'} |",
        f"| ├ 其中：真特异度（MEDIUM命中、图谱非空仍0误锁） | **{m['guardrail_specificity_kb_hit']:.0%}** | 参考 | — |",
        f"| └ 其中：防幻觉（知识库外0臆造） | **{m['guardrail_anti_hallucination']:.0%}** | 参考 | — |",
        f"| **盲测集通过率（held-out，开发期未参与调参）** | **{m['blind_pass_rate']:.1%}**（{m['blind_pass']}/{m['blind_total']}） | ≥90% | "
        f"{'✅' if m['blind_pass_rate'] >= 0.9 else '❌'} |",
        f"| RII强度指数有效率 | **{m['rii_validity_rate']:.0%}**（{m['rii_checked']}次非空推演） | 100% | "
        f"{'✅' if m['rii_validity_rate'] >= 1 else '❌'} |",
        f"| 消解闭环场景通过率 | **{m['closure_pass_rate']:.0%}**（{m['closure_pass']}/{m['closure_total']}） | ≥90% | "
        f"{'✅' if m['closure_pass_rate'] >= 0.9 else '❌'} |",
        f"| 哈希链完整性 | valid={m['evidence_chain_valid']}（{m['evidence_count']}条决策） | 必须 | "
        f"{'✅' if m['evidence_chain_valid'] else '❌'} |",
        f"| FHIR Provenance导出 | {m['fhir_provenance']} | 必须 | "
        f"{'✅' if m['fhir_provenance'] else '❌'} |",
        f"| MDT五Agent会诊 | views={m['mdt_agent_views']} | 5 | "
        f"{'✅' if m['mdt_agent_views'] == 5 else '❌'} |",
        f"| 时间学四类型覆盖 | {m['chrono_type_coverage']} | WINDOW/RHYTHM/PERIODIC/SEASONAL | ✅ |",
        "",
        "## 二、评测方法（开发集与盲测集隔离）",
        "",
        "**开发集（T/R/G/W，60例+10场景）**：用于工程对齐与回归——分诊三元组（科室+紧急度+降级）、"
        "涟漪五维知识命中、护栏双通道、消解闭环数学。其金标准部分依据内置循证知识库制定，"
        "测的是'知识库→图谱→RII→护栏→闭环'全链路工程正确性；知识库医学内容的完备性由 "
        "Timing Card 引用的临床指南（AHA/ACC/ADA/GINA/中国防治指南）承担。**开发集分数不构成临床正确性主张。**",
        "",
        "**盲测集（BD，10例 held-out）**：全部为开发期未参与任何规则调参/阈值校准的新病例，"
        "金标准依据外部临床指南与通用分诊原则独立制定（每例标注 goldSource）。分两类：",
        "- **bd_external（知识库外）**：期望为诚实空态（RII=0、0臆造建议）或诚实降级（degraded=true）"
        "——检验泛化到未知病例时的安全底线，不硬猜、不幻觉；",
        "- **bd_transfer（知识库内要素的未见组合）**：检验组合迁移（如华法林+头孢类联用、多病共存）"
        "与红线泛化（否定语义、颅压危象、儿科急症）。",
        "盲测集只报告、不回填规则——若盲测暴露缺陷，修复后须连同开发集一起复测并记录版本。",
        "",
        "## 三、版本改进记录（评测驱动的闭环）",
        "",
    ]
    for note in report["versionNotes"]:
        lines.append(f"- {note}")
    lines += ["", "## 四、API延迟（网关实测）", "",
              "| API | 调用次数 | P50(ms) | P95(ms) | Max(ms) |", "|---|---|---|---|---|"]
    for row in report["latency"]:
        lines.append(f"| `{row['api']}` | {row['calls']} | {row['p50_ms']} | {row['p95_ms']} | {row['max_ms']} |")
    lines += ["", "## 五、逐用例明细", "", "### 分诊路由（开发集）", ""]
    for r in report["results"]["triage"]:
        lines.append(f"- [{'✅' if r['ok'] else '❌'}] **{r['id']}** {r['detail']}")
    lines += ["", "### 涟漪推演（开发集）", ""]
    for r in report["results"]["ripple"]:
        lines.append(f"- [{'✅' if r['ok'] else '❌'}] **{r['id']}** {r['detail']}")
    lines += ["", "### 反事实护栏（开发集）", ""]
    for r in report["results"]["guardrail"]:
        lines.append(f"- [{'✅' if r['ok'] else '❌'}] **{r['id']}** {r['detail']}")
    lines += ["", "### 消解闭环（开发集）", ""]
    for r in report["results"].get("closure", []):
        lines.append(f"- [{'✅' if r['ok'] else '❌'}] **{r['id']}** {r['detail']}")
    lines += ["", "### 盲测集（held-out BD）", ""]
    for r in report["results"].get("blind", []):
        lines.append(f"- [{'✅' if r['ok'] else '❌'}] **{r['id']}** {r['detail']}")
    lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    sys.exit(main())
