#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
安全自审（Security Self-Audit）：扫描仓库中的硬编码凭据风险并出具报告。

与 CI 同源（.github/workflows/ci.yml security-self-audit job 调用同一脚本）。
口径（保守：只报高置信模式，把"演示种子凭据/环境变量默认值"与"真实泄漏"区分开）：

  1. 高危模式：password/secret/token/apikey 赋值为「非占位、非引用」的字面量（长度≥8）；
  2. 私钥块出现在追踪文件中；
  3. 连接串内嵌凭据（scheme://user:pass@host）；
  4. 被 git 追踪的 .env 文件。

以下归入「已知披露（本地默认/演示种子）」不算泄漏，但生产必须覆盖：
  - ${VAR:-default} 形式的部署默认值（compose 等价 env 注入位）；
  - e2e 演示账号（doctor1/123456、种子患者 13800000001）、本地内部令牌默认值。
"""
import argparse
import datetime
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

SCAN_SUFFIXES = {".java", ".py", ".ts", ".js", ".mjs", ".vue", ".yml", ".yaml", ".md", ".sql", ".properties", ".conf", ".sh", ".ps1"}
SKIP_DIRS = {".git", "node_modules", "dist", "target", ".zcode", "__pycache__", "docs-assets"}

ASSIGN_RE = re.compile(
    r"""(?i)(?P<key>(?:password|passwd|secret|token|apikey|api_key|access[_-]?key))\s*[:=]\s*["']?(?P<val>[^"'\s]{8,})["']?"""
)
PLACEHOLDER_RE = re.compile(
    r"""(?i)(change[-_]?me|changeme|placeholder|your[-_]|xxx|example|\$\{|%\{|\{\{|<[^>]+>|\btest\b|e2e_test|123456|dummy|sample)"""
)
NON_LITERAL_RE = re.compile(
    r"""(\(|\)|\$\{|\$[A-Z]|\bString\b|\bfinal\b|resolve|expected|request|author|get[A-Z]|base64|encode|getenv|Environment|os\.env)"""
)
ENV_DEFAULT_RE = re.compile(r"\$\{[A-Z_][A-Z0-9_]*:-")
REF_VALUE_RE = re.compile(r"^(args|body|jwt|this|process|req|res|config|params|options|env)[.]")
CONST_REF_RE = re.compile(r"^[A-Z][A-Z0-9_]*$")
NON_ASCII_RE = re.compile(r"[^\x00-\x7f]")
COMMENT_LINE_RE = re.compile(r"^\s*(//|#|/\*|\*)")
KNOWN_DISCLOSURE_RE = re.compile(r"""(?i)(doctor1|13800000001|123456|smart-cloud-brain-internal-local-token)""")
PRIVATE_KEY_RE = re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")
CONN_RE = re.compile(r"(?:jdbc:[a-z0-9]+://|postgres(?:ql)?://|mysql://)[^\s\"']*:[^\s\"']+@")


def entry_placeholder(val, rel, i, key):
    return f"{rel}:{i}: {key}={val[:6]}***"


def tracked_files():
    out = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True).stdout
    return [Path(ROOT, f) for f in out.splitlines() if f.strip()]


def scan():
    findings, known, env_tracked = [], [], []
    for path in tracked_files():
        rel = path.relative_to(ROOT)
        if any(part in SKIP_DIRS for part in rel.parts):
            continue
        if path.suffix.lower() == ".env" or path.name in {".env", ".env.local"}:
            env_tracked.append(str(rel))
            continue
        if path.suffix.lower() not in SCAN_SUFFIXES:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for i, line in enumerate(text.splitlines(), 1):
            if PRIVATE_KEY_RE.search(line):
                findings.append(f"{rel}:{i}: 私钥块")
            for m in CONN_RE.finditer(line):
                findings.append(f"{rel}:{i}: 连接串内嵌凭据 {m.group(0)[:40]}***")
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("#") or stripped.startswith("/*") or stripped.startswith("*"):
                continue  # 注释行不参与凭据判定
            for m in ASSIGN_RE.finditer(line):
                val = m.group("val")
                if PLACEHOLDER_RE.search(val):
                    continue
                if NON_ASCII_RE.search(val):
                    continue  # 含非ASCII：注释文本误匹配
                if REF_VALUE_RE.match(val) or CONST_REF_RE.match(val):
                    # 属性/常量引用形态（DOCTOR_PASSWORD、process.env 等）
                    known.append(entry_placeholder(val, rel, i, m.group("key")))
                    continue
                entry = entry_placeholder(val, rel, i, m.group("key"))
                if ENV_DEFAULT_RE.search(line) or NON_LITERAL_RE.search(val):
                    known.append(entry + "  [环境默认/代码引用]")
                    continue
                (known if KNOWN_DISCLOSURE_RE.search(line) else findings).append(entry)
    return findings, known, env_tracked


def report(findings, known, env_tracked) -> str:
    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    lines = [
        "# 安全自审报告 SECURITY-SELF-AUDIT",
        "",
        f"> 生成于 {now} · 脚本：scripts/security_self_audit.py（CI 同源规则，可复跑）",
        "> 口径：只报高置信硬编码凭据模式；演示种子凭据与环境变量默认值单独披露（见下），不算泄漏但生产必须覆盖。",
        "",
        f"## 结果：{'✅ 未发现硬编码凭据泄漏' if not findings else f'⚠ 发现 {len(findings)} 处待处理'}",
        "",
        "- 扫描范围：git 追踪的源码/配置/文档（跳过依赖与构建产物）",
        f"- 高危发现：{len(findings)} 处",
        f"- 已知披露（演示种子/环境默认/代码引用形态）：{len(known)} 处",
        f"- 被追踪的 .env 文件：{len(env_tracked)} 个",
        "",
        "## 待处理发现",
        "",
    ]
    lines += [f"- {f}" for f in findings] if findings else ["（无）"]
    lines += ["", "## 已知披露（演示种子/环境默认，生产必须覆盖轮换）", ""]
    lines += [f"- {k}" for k in known] if known else ["（无）"]
    if env_tracked:
        lines += ["", "## 被追踪的 .env", ""] + [f"- {e}" for e in env_tracked]
    lines += [
        "",
        "## 生产部署要求（deploy/env/ 模板）",
        "",
        "- JWT 密钥、内部服务令牌、RIPPLE_SHARE_SECRET（家属圈签名）、数据库/RabbitMQ 口令全部经环境变量注入；",
        "- 上述「演示种子」仅用于本地演示与自动化测试，任何对外部署必须先覆盖并轮换。",
        "",
    ]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="写入 docs/SECURITY-SELF-AUDIT.md")
    args = parser.parse_args()
    findings, known, env_tracked = scan()
    text = report(findings, known, env_tracked)
    print(text)
    if args.write:
        out = ROOT / "docs" / "SECURITY-SELF-AUDIT.md"
        out.write_text(text, encoding="utf-8")
        print(f"\nwritten -> {out}")
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
