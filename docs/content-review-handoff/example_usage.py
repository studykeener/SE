# -*- coding: utf-8 -*-
"""Web/App 提交评论示例（Python + PyMySQL）。"""

import pymysql

from content_review_engine import (
    SensitiveLevel,
    SensitiveWord,
    ReviewDecision,
    normalize_source,
    submit_comment,
    submit_photo,
    strategy_from_db,
)


def load_sensitive_words(conn) -> list[SensitiveWord]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT word, level FROM sensitive_words WHERE enabled = 1 ORDER BY word ASC"
        )
        rows = cur.fetchall()
    result = []
    for word, level in rows:
        lv = SensitiveLevel.SEVERE if str(level).upper() == "SEVERE" else SensitiveLevel.LIGHT
        result.append(SensitiveWord(word=word, level=lv))
    return result


def load_strategy(conn):
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT low_risk_max_score, medium_risk_max_score,
                   low_risk_action, medium_risk_action, high_risk_action
            FROM review_strategy_config WHERE id = 1
            """
        )
        row = cur.fetchone()
    if not row:
        return strategy_from_db({})
    cols = [
        "low_risk_max_score", "medium_risk_max_score",
        "low_risk_action", "medium_risk_action", "high_risk_action",
    ]
    return strategy_from_db(dict(zip(cols, row)))


def user_exists(conn, user_id: int) -> bool:
    with conn.cursor() as cur:
        cur.execute("SELECT 1 FROM user WHERE user_id = %s LIMIT 1", (user_id,))
        return cur.fetchone() is not None


def post_comment(conn, user_id, museum_id, object_id, content, source_system="web"):
    if not user_exists(conn, user_id):
        raise ValueError(f"用户不存在: {user_id}")

    words = load_sensitive_words(conn)
    strategy = load_strategy(conn)
    result = submit_comment(content, words, strategy)

    if result.decision == ReviewDecision.REJECT:
        return {"ok": False, "message": result.user_message}

    source = normalize_source(source_system)
    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO comment (
              user_id, museum_id, object_id, content, source,
              audit_method, audit_status, auto_audit_status, sensitive_words_hit, status
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 1)
            """,
            (
                user_id, museum_id, object_id, content, source,
                result.audit_method, result.audit_status,
                result.auto_audit_status, result.sensitive_words_hit,
            ),
        )
    conn.commit()
    return {"ok": True, "message": result.user_message}


def post_photo(conn, user_id, photo_url, description=None, museum_id=None, object_id=None, source_system="web"):
    if not user_exists(conn, user_id):
        raise ValueError(f"用户不存在: {user_id}")

    words = load_sensitive_words(conn)
    result = submit_photo(description, photo_url, words)
    source = normalize_source(source_system)

    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO user_upload_photo (
              user_id, museum_id, object_id, photo_url, description, source,
              status, audit_method, auto_audit_status, auto_audit_score
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (
                user_id, museum_id, object_id, photo_url, description, source,
                result.status, result.audit_method,
                result.auto_audit_status, result.auto_audit_score,
            ),
        )
    conn.commit()
    return {"ok": True, "message": result.user_message}


if __name__ == "__main__":
    conn = pymysql.connect(
        host="47.96.152.190",
        port=3306,
        user="your_user",
        password="your_password",
        database="overseas_chinese_artifacts",
        charset="utf8mb4",
    )
    try:
        print(post_comment(conn, 1001, 1, "ld1-xxx", "这个文物很精美！"))
    finally:
        conn.close()
