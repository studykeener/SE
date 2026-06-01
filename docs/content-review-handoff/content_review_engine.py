# -*- coding: utf-8 -*-
"""
与子系统5 admin-backend ReviewQueueService 逻辑一致。
Web/App 可直接复制本文件使用。
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Iterable, List, Optional, Sequence, Tuple


class SensitiveLevel(str, Enum):
    LIGHT = "LIGHT"
    SEVERE = "SEVERE"


class AutoReviewAction(str, Enum):
    AUTO_APPROVE = "AUTO_APPROVE"
    AUTO_REJECT = "AUTO_REJECT"
    MANUAL_REVIEW = "MANUAL_REVIEW"


class ReviewDecision(str, Enum):
    INSERT_APPROVED = "INSERT_APPROVED"
    INSERT_PENDING = "INSERT_PENDING"
    REJECT = "REJECT"


@dataclass(frozen=True)
class SensitiveWord:
    word: str
    level: SensitiveLevel = SensitiveLevel.LIGHT


@dataclass(frozen=True)
class ReviewStrategy:
    low_risk_max_score: int = 20
    medium_risk_max_score: int = 60
    low_risk_action: AutoReviewAction = AutoReviewAction.AUTO_APPROVE
    medium_risk_action: AutoReviewAction = AutoReviewAction.MANUAL_REVIEW
    high_risk_action: AutoReviewAction = AutoReviewAction.AUTO_REJECT


@dataclass(frozen=True)
class RiskResult:
    score: int
    hits: Optional[str]


@dataclass(frozen=True)
class CommentReviewResult:
    decision: ReviewDecision
    audit_status: int
    audit_method: int
    auto_audit_status: int
    sensitive_words_hit: Optional[str]
    user_message: str


@dataclass(frozen=True)
class PhotoReviewResult:
    status: int
    audit_method: int
    auto_audit_status: int
    auto_audit_score: float
    sensitive_words_hit: Optional[str]
    user_message: str


def normalize_source(source_system: Optional[str]) -> str:
    if not source_system or not source_system.strip():
        return "web"
    return "app" if source_system.strip().lower() == "app" else "web"


def _count_occurrences(text: str, keyword: str) -> int:
    if not text or not keyword:
        return 0
    count = 0
    idx = 0
    while True:
        found = text.find(keyword, idx)
        if found < 0:
            break
        count += 1
        idx = found + len(keyword)
    return count


def _contains_any(text: str, keys: Sequence[str]) -> bool:
    return any(k in text for k in keys)


def _clamp(score: int) -> int:
    return max(0, min(100, score))


def compute_risk(
    text: Optional[str],
    url: Optional[str],
    image: bool,
    enabled_words: Iterable[SensitiveWord],
    external_image_score: Optional[int] = None,
) -> RiskResult:
    words = sorted(
        [w for w in enabled_words if w.word and w.word.strip()],
        key=lambda w: w.word,
    )
    all_text = f"{text or ''} {url or ''}".lower()
    hit_words: List[str] = []
    light_hits = 0

    for w in words:
        hits = _count_occurrences(all_text, w.word.lower())
        if hits <= 0:
            continue
        hit_words.append(w.word)
        if w.level == SensitiveLevel.SEVERE:
            return RiskResult(100, ",".join(hit_words))
        light_hits += hits

    score = light_hits * 10

    if image and external_image_score is not None:
        score = max(score, external_image_score)

    if image and _contains_any(
        all_text,
        ("childporn", "terror", "爆炸物", "恋童", "极端暴力", "严重违规"),
    ):
        return RiskResult(100, ",".join(hit_words) if hit_words else None)

    if image and _contains_any(
        all_text, ("violence", "porn", "bloody", "涉黄", "暴力", "违规")
    ):
        score += 10

    hits_str = ",".join(hit_words) if hit_words else None
    return RiskResult(_clamp(score), hits_str)


def _resolve_action(risk_score: int, strategy: ReviewStrategy) -> AutoReviewAction:
    if risk_score <= strategy.low_risk_max_score:
        return strategy.low_risk_action
    if risk_score <= strategy.medium_risk_max_score:
        return strategy.medium_risk_action
    return strategy.high_risk_action


def review_comment(risk_score: int, strategy: Optional[ReviewStrategy] = None) -> CommentReviewResult:
    cfg = strategy or ReviewStrategy()
    action = _resolve_action(risk_score, cfg)
    if action == AutoReviewAction.AUTO_REJECT:
        return CommentReviewResult(
            ReviewDecision.REJECT, 2, 1, _clamp(risk_score), None,
            "内容违规无法发布，请修改后重试",
        )
    if action == AutoReviewAction.AUTO_APPROVE:
        return CommentReviewResult(
            ReviewDecision.INSERT_APPROVED, 1, 3, _clamp(risk_score), None,
            "审核通过，可以展示",
        )
    return CommentReviewResult(
        ReviewDecision.INSERT_PENDING, 0, 1, _clamp(risk_score), None,
        "已提交，等待人工审核",
    )


def submit_comment(
    content: str,
    enabled_words: Iterable[SensitiveWord],
    strategy: Optional[ReviewStrategy] = None,
) -> CommentReviewResult:
    risk = compute_risk(content, None, False, enabled_words)
    base = review_comment(risk.score, strategy)
    message = base.user_message
    if base.decision == ReviewDecision.REJECT and risk.hits:
        message = f"内容违规无法发布，请修改后重试（命中：{risk.hits}）"
    return CommentReviewResult(
        base.decision, base.audit_status, base.audit_method,
        _clamp(risk.score), risk.hits, message,
    )


def submit_photo(
    description: Optional[str],
    photo_url: str,
    enabled_words: Iterable[SensitiveWord],
    external_image_score: Optional[int] = None,
) -> PhotoReviewResult:
    text = description if description and description.strip() else photo_url
    risk = compute_risk(text, photo_url, True, enabled_words, external_image_score)
    score = _clamp(risk.score)
    auto_audit_status = 2 if score >= 60 else 1
    return PhotoReviewResult(
        status=0,
        audit_method=2,
        auto_audit_status=auto_audit_status,
        auto_audit_score=float(score),
        sensitive_words_hit=risk.hits,
        user_message="已提交，等待人工审核",
    )


def strategy_from_db(row: dict) -> ReviewStrategy:
    defaults = ReviewStrategy()

    def action(key: str, fallback: AutoReviewAction) -> AutoReviewAction:
        raw = row.get(key)
        if not raw:
            return fallback
        try:
            return AutoReviewAction(str(raw).strip().upper())
        except ValueError:
            return fallback

    return ReviewStrategy(
        low_risk_max_score=int(row.get("low_risk_max_score") or defaults.low_risk_max_score),
        medium_risk_max_score=int(row.get("medium_risk_max_score") or defaults.medium_risk_max_score),
        low_risk_action=action("low_risk_action", defaults.low_risk_action),
        medium_risk_action=action("medium_risk_action", defaults.medium_risk_action),
        high_risk_action=action("high_risk_action", defaults.high_risk_action),
    )
