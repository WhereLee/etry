#!/usr/bin/env python3
"""把 surefire 报告汇总成 Markdown，写进 GitHub Actions 的 Job Summary。

用法: python3 .github/scripts/surefire-summary.py server/target/surefire-reports

单独放成文件而不是内联在 workflow 里，是为了能在本地直接跑一遍看效果，
也避免把 heredoc 和 YAML 缩进搅在一起。
"""

import glob
import os
import sys
import xml.etree.ElementTree as ET
from xml.parsers import expat

# surefire 报告是 Maven 自己生成的，理论上不可控输入，
# 但解析前仍然加两层限制：限制体积、不解析外部实体与 DTD。
MAX_REPORT_BYTES = 5 * 1024 * 1024


def parse_root(path: str):
    if os.path.getsize(path) > MAX_REPORT_BYTES:
        raise ValueError(f"报告体积异常，跳过: {path}")
    parser = ET.XMLParser()
    try:
        # 不解析参数实体，外部 DTD 不会被拉取，也阻断实体膨胀
        parser.parser.SetParamEntityParsing(expat.XML_PARAM_ENTITY_PARSING_NEVER)
    except AttributeError:
        pass  # 非 CPython 实现下退化为默认行为
    with open(path, "rb") as handle:
        return ET.parse(handle, parser=parser).getroot()


def short_name(class_name: str) -> str:
    return class_name.rsplit(".", 1)[-1]


def main() -> int:
    report_dir = sys.argv[1] if len(sys.argv) > 1 else "server/target/surefire-reports"
    files = sorted(glob.glob(os.path.join(report_dir, "TEST-*.xml")))

    lines = ["## 测试结果", ""]

    if not files:
        lines.append(f"没有找到 surefire 报告（`{report_dir}` 为空），通常是编译失败导致测试没执行。")
        write_summary(lines)
        return 0

    lines.append("| 测试类 | 用例 | 失败 | 错误 | 跳过 | 耗时(s) |")
    lines.append("|---|---:|---:|---:|---:|---:|")

    total = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    total_time = 0.0
    failed_cases: list[tuple[str, str, str]] = []

    for path in files:
        root = parse_root(path)
        name = short_name(root.get("name", os.path.basename(path)))
        tests = int(root.get("tests", 0))
        failures = int(root.get("failures", 0))
        errors = int(root.get("errors", 0))
        skipped = int(root.get("skipped", 0))
        seconds = float(root.get("time", 0))

        total["tests"] += tests
        total["failures"] += failures
        total["errors"] += errors
        total["skipped"] += skipped
        total_time += seconds

        lines.append(f"| {name} | {tests} | {failures} | {errors} | {skipped} | {seconds:.3f} |")

        for case in root.findall("testcase"):
            for kind in ("failure", "error"):
                node = case.find(kind)
                if node is not None:
                    message = (node.get("message") or "").strip().splitlines()
                    failed_cases.append((name, case.get("name", "?"), message[0][:180] if message else kind))

    lines.append(
        f"| **合计** | **{total['tests']}** | **{total['failures']}** | "
        f"**{total['errors']}** | **{total['skipped']}** | **{total_time:.3f}** |"
    )

    if failed_cases:
        lines += ["", "### 失败用例", "", "| 测试类 | 用例 | 信息 |", "|---|---|---|"]
        for cls, case, message in failed_cases:
            # 消息里的 | 会打断表格，统一换掉
            safe = message.replace("|", "\\|")
            lines.append(f"| {cls} | {case} | {safe} |")

    write_summary(lines)
    return 0


def write_summary(lines: list[str]) -> None:
    text = "\n".join(lines) + "\n"
    print(text)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as handle:
            handle.write(text)


if __name__ == "__main__":
    sys.exit(main())
