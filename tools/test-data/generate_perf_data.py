#!/usr/bin/env python3
"""Generate deterministic order/payment/playback data for SQL EXPLAIN audits.

The output is plain MySQL SQL, intentionally kept dependency-free so it can run
on the staging host without Python database drivers.
"""

from __future__ import annotations

import argparse
from pathlib import Path


def sql(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--orders", type=int, default=10000)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()

    lines: list[str] = []

    def out(line: str = "") -> None:
        lines.append(line)

    def batched(prefix: str, rows: list[str]) -> None:
        for index in range(0, len(rows), 200):
            lines.append(prefix + " VALUES")
            lines.append(",\n".join(rows[index:index + 200]) + ";")

    out("SET FOREIGN_KEY_CHECKS=0;")
    for table in (
        "t_playback_log", "t_admin_audit_log", "t_alert_log",
        "t_refund", "t_payment_event", "t_payment_attempt", "t_order",
        "t_student_series", "t_learning_progress",
        "t_series_video", "t_series_chapter", "t_series",
        "t_teacher_invitation", "t_user",
    ):
        out(f"TRUNCATE TABLE {table};")
    out("SET FOREIGN_KEY_CHECKS=1;")

    teachers = [f"( {i}, 'perf_teacher_{i}', 'perf', '性能老师{i}', 'TEACHER', 'ACTIVE', FALSE, 0 )" for i in range(1, 101)]
    students = [f"( {100 + i}, 'perf_student_{i}', 'perf', '性能学生{i}', 'STUDENT', 'ACTIVE', FALSE, 0 )" for i in range(1, 101)]
    batched("INSERT INTO t_user (id, username, password_hash, nickname, role, status, must_change_password, token_version)",
            teachers + students)

    series = [
        f"( {i}, {i}, '性能系列{i}', '性能测试系列{i}', 1, 'PUBLISHED', NOW(3), 'ON_SALE', 199, 'CNY', NOW(3) )"
        for i in range(1, 101)
    ]
    batched("INSERT INTO t_series (id, teacher_id, title, summary, sort, status, published_at, sale_status, price_cents, currency, sale_updated_at)", series)

    chapters = [f"( {i}, {i}, '性能章节{i}', 1 )" for i in range(1, 101)]
    batched("INSERT INTO t_series_chapter (id, series_id, title, sort)", chapters)
    videos = [f"( {i}, {i}, '性能视频{i}', 1, 'perf/video-{i}.mp4', 'READY', 1800 )" for i in range(1, 101)]
    batched("INSERT INTO t_series_video (id, chapter_id, title, sort, video_key, media_status, duration_seconds)", videos)

    order_rows: list[str] = []
    attempt_rows: list[str] = []
    event_rows: list[str] = []
    refund_rows: list[str] = []
    entitlement_rows: list[str] = []
    fulfilled_order_ids: list[int] = []

    for n in range(1, args.orders + 1):
        user_id = 100 + ((n - 1) % 100) + 1
        series_id = ((n - 1) // 100) % 100 + 1
        bucket = (n - 1) % 20
        if bucket < 14:
            status = "FULFILLED"
        elif bucket < 16:
            status = "PAID"
        elif bucket < 18:
            status = "PAYING"
        elif bucket == 18:
            status = "CREATED"
        else:
            status = "CLOSED"

        active_key = f"'{user_id}:{series_id}'" if status in {"PAYING", "PAID", "CREATED"} else "NULL"
        expire_expr = "DATE_SUB(NOW(3), INTERVAL 10 MINUTE)" if n % 2 == 0 else "DATE_ADD(NOW(3), INTERVAL 15 MINUTE)"
        updated_expr = "DATE_SUB(NOW(3), INTERVAL 30 MINUTE)" if status == "PAYING" and n % 3 == 0 else "NOW(3)"
        paid_expr = "DATE_SUB(NOW(3), INTERVAL {0} MINUTE)".format(n % 1440) if status in {"PAID", "FULFILLED"} else "NULL"
        fulfilled_expr = "DATE_SUB(NOW(3), INTERVAL {0} MINUTE)".format(max(0, n % 1440 - 1)) if status == "FULFILLED" else "NULL"
        closed_expr = "NOW(3)" if status == "CLOSED" else "NULL"
        order_no = f"PERFORD{n:08d}"
        order_rows.append(
            f"( {n}, {sql(order_no)}, {user_id}, {series_id}, {sql(f'性能系列{series_id}')}, 199, 'CNY', {sql(status)}, "
            f"'MOCK', NULL, {sql(f'perf-req-{n}')}, {active_key}, {expire_expr}, {paid_expr}, {fulfilled_expr}, {closed_expr}, "
            f"0, NULL, DATE_SUB(NOW(3), INTERVAL {n % 1440} MINUTE), {updated_expr}, 0 )"
        )
        if status == "FULFILLED":
            fulfilled_order_ids.append(n)
            entitlement_rows.append(
                f"( {user_id}, {series_id}, 1, 'ADMIN', 'ACTIVE', NULL, NOW(3), NOW(3) )"
            )

        if status != "CREATED":
            attempt_status = "PROCESSING" if status == "PAYING" else ("FAILED" if status == "CLOSED" else "SUCCESS")
            provider_status = "CREATED" if attempt_status == "PROCESSING" else ("FAILED" if attempt_status == "FAILED" else "PAID")
            finished = "NULL" if attempt_status == "PROCESSING" else "NOW(3)"
            attempt_rows.append(
                f"( {n}, {n}, 1, 'MOCK', {sql(attempt_status)}, {sql(f'PERFTRADE{n:08d}')}, {sql(provider_status)}, 199, NULL, NULL, NULL, NULL, NOW(3), {finished} )"
            )

        if n <= args.orders // 2:
            for event_no in range(1, 6):
                event_type = "PAYMENT_SUCCESS" if status in {"PAID", "FULFILLED"} else "PAYMENT_FAILED"
                process_status = "FAILED" if n % 97 == 0 else "PROCESSED"
                event_rows.append(
                    f"( 'MOCK', {sql(f'PERF-EV-{n}-{event_no}')}, {n}, {sql(order_no)}, {sql(event_type)}, 199, NULL, {sql(process_status)}, NULL, "
                    f"DATE_SUB(NOW(3), INTERVAL {(n + event_no) % 1440} MINUTE), NOW(3) )"
                )

    batched("INSERT INTO t_order (id, order_no, user_id, series_id, series_title_snapshot, price_cents, currency, status, pay_channel, provider_trade_no, client_request_id, active_key, expire_at, paid_at, fulfilled_at, closed_at, fulfillment_attempts, last_error, created_at, updated_at, version)", order_rows)
    batched("INSERT INTO t_payment_attempt (id, order_id, attempt_no, channel, status, provider_trade_no, provider_status, provider_amount_cents, request_digest, failure_code, failure_message, mock_scenario, created_at, finished_at)", attempt_rows)
    batched("INSERT INTO t_payment_event (provider, event_key, order_id, order_no, event_type, amount_cents, payload_digest, process_status, error_message, received_at, processed_at)", event_rows)
    batched("INSERT INTO t_student_series (user_id, series_id, sort, source, status, order_id, valid_from, granted_at)", entitlement_rows)

    for index, order_id in enumerate(fulfilled_order_ids[:1000], start=1):
        status = "SUCCEEDED" if index % 10 != 0 else ("FAILED" if index % 2 == 0 else "PROCESSING")
        attempt_count = 5 if status == "FAILED" and index % 20 == 0 else index % 3
        next_retry = "DATE_SUB(NOW(3), INTERVAL 1 MINUTE)" if status == "FAILED" else "NULL"
        succeeded = "NOW(3)" if status == "SUCCEEDED" else "NULL"
        provider_refund = f"'PERFREFUND{index:08d}'" if status == "SUCCEEDED" else "NULL"
        updated_at = "DATE_SUB(NOW(3), INTERVAL 30 MINUTE)" if status == "PROCESSING" else "NOW(3)"
        refund_rows.append(
            f"( {sql(f'PERFREF{index:08d}')}, {order_id}, 199, {sql(status)}, {attempt_count}, {next_retry}, {provider_refund}, '性能退款', 1, "
            f"DATE_SUB(NOW(3), INTERVAL {index % 1440} MINUTE), {succeeded}, NULL, {updated_at} )"
        )
    batched("INSERT INTO t_refund (refund_no, order_id, amount_cents, status, attempt_count, next_retry_at, provider_refund_no, reason, requested_by, requested_at, succeeded_at, error_message, updated_at)", refund_rows)

    playback_rows: list[str] = []
    for n in range(1, args.orders + 1):
        user_id = 100 + ((n - 1) % 100) + 1
        video_id = ((n - 1) % 100) + 1
        series_id = ((n - 1) // 100) % 100 + 1
        event_type = ("URL_REQUEST", "PLAY", "PROGRESS", "ERROR")[(n - 1) % 4]
        playback_rows.append(
            f"( {user_id}, {video_id}, {series_id}, 'BACKEND', {sql(event_type)}, {n % 1800}, 1800, {n % 2}, NULL, 'perf-agent', {sql(f'perf-session-{n}')}, DATE_SUB(NOW(3), INTERVAL {n % 30} DAY) )"
        )
    batched("INSERT INTO t_playback_log (user_id, video_id, series_id, source, event_type, position_seconds, duration_seconds, cache_hit, message, user_agent, session_id, created_at)", playback_rows)

    progress_rows: list[str] = []
    for n in range(1, args.orders + 1):
        user_id = 101 + ((n - 1) // 100) % 100
        series_id = ((n - 1) // 100) % 100 + 1
        video_id = ((n - 1) % 100) + 1
        progress_rows.append(
            f"( {user_id}, {series_id}, {video_id}, {n % 1800}, 1800, {1 if n % 4 == 0 else 0}, DATE_SUB(NOW(3), INTERVAL {n % 30} DAY), NOW(3) )"
        )
    batched("INSERT INTO t_learning_progress (user_id, series_id, video_id, position_seconds, duration_seconds, completed, created_at, updated_at)", progress_rows)

    audit_rows: list[str] = []
    for n in range(1, min(args.orders, 5000) + 1):
        audit_rows.append(
            f"( {1 + (n % 100)}, {sql('PERF_ACTION_' + str(n % 20))}, 'ORDER', {1 if n % 10 else 0}, {n % 1000}, NULL, DATE_SUB(NOW(3), INTERVAL {n % 1440} MINUTE) )"
        )
    batched("INSERT INTO t_admin_audit_log (operator_id, action, target_type, success, duration_ms, error_message, created_at)", audit_rows)

    out("ANALYZE TABLE t_order, t_payment_attempt, t_payment_event, t_refund, t_playback_log, t_learning_progress, t_admin_audit_log;")
    out("SELECT 'perf data ready' AS message, COUNT(*) AS orders FROM t_order;")

    text = "\n".join(lines) + "\n"
    if args.out:
        args.out.write_text(text, encoding="utf-8")
    else:
        print(text, end="")


if __name__ == "__main__":
    main()
