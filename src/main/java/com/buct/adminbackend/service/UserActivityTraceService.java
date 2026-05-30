package com.buct.adminbackend.service;

import com.buct.adminbackend.dto.UserActivityTraceItem;
import com.buct.adminbackend.entity.Comment;
import com.buct.adminbackend.entity.UserFavorite;
import com.buct.adminbackend.entity.UserLike;
import com.buct.adminbackend.entity.UserUploadPhoto;
import com.buct.adminbackend.repository.CommentRepository;
import com.buct.adminbackend.repository.UserFavoriteRepository;
import com.buct.adminbackend.repository.UserLikeRepository;
import com.buct.adminbackend.repository.UserUploadPhotoRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserActivityTraceService {

    public static final String TYPE_COMMENT = "COMMENT";
    public static final String TYPE_UPLOAD = "UPLOAD";
    public static final String TYPE_FAVORITE = "FAVORITE";
    public static final String TYPE_LIKE = "LIKE";

    private final CommentRepository commentRepository;
    private final UserUploadPhotoRepository userUploadPhotoRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final UserLikeRepository userLikeRepository;

    public Page<UserActivityTraceItem> list(Long userId, String type, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        List<UserActivityTraceItem> items = new ArrayList<>();
        String normalizedType = normalizeType(type);

        if (includeType(normalizedType, TYPE_COMMENT)) {
            commentRepository.findAll(buildCommentSpec(userId, from, to)).stream()
                    .map(this::toCommentItem)
                    .forEach(items::add);
        }
        if (includeType(normalizedType, TYPE_UPLOAD)) {
            userUploadPhotoRepository.findAll(buildPhotoSpec(userId, from, to)).stream()
                    .map(this::toPhotoItem)
                    .forEach(items::add);
        }
        if (includeType(normalizedType, TYPE_FAVORITE)) {
            userFavoriteRepository.findAll(buildFavoriteSpec(userId, from, to)).stream()
                    .map(this::toFavoriteItem)
                    .forEach(items::add);
        }
        if (includeType(normalizedType, TYPE_LIKE)) {
            userLikeRepository.findAll(buildLikeSpec(userId, from, to)).stream()
                    .map(this::toLikeItem)
                    .forEach(items::add);
        }

        items.sort(Comparator.comparing(UserActivityTraceItem::activityTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UserActivityTraceItem::recordId, Comparator.nullsLast(Comparator.reverseOrder())));

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());
        List<UserActivityTraceItem> pageContent = start >= items.size() ? List.of() : items.subList(start, end);
        return new PageImpl<>(pageContent, pageable, items.size());
    }

    private UserActivityTraceItem toCommentItem(Comment c) {
        return new UserActivityTraceItem(
                c.getId(),
                "comment",
                TYPE_COMMENT,
                c.getContent(),
                c.getMuseumId(),
                c.getObjectId(),
                c.getSource(),
                c.getAuditStatus(),
                c.getCreatedAt()
        );
    }

    private UserActivityTraceItem toPhotoItem(UserUploadPhoto p) {
        String summary = StringUtils.hasText(p.getDescription()) ? p.getDescription() : p.getPhotoUrl();
        return new UserActivityTraceItem(
                p.getId(),
                "user_upload_photo",
                TYPE_UPLOAD,
                summary,
                p.getMuseumId(),
                p.getObjectId(),
                p.getSource(),
                p.getStatus(),
                p.getCreatedAt()
        );
    }

    private UserActivityTraceItem toFavoriteItem(UserFavorite f) {
        return new UserActivityTraceItem(
                f.getId(),
                "user_favorite",
                TYPE_FAVORITE,
                "收藏文物",
                f.getMuseumId(),
                f.getObjectId(),
                null,
                null,
                f.getCreatedAt()
        );
    }

    private UserActivityTraceItem toLikeItem(UserLike l) {
        return new UserActivityTraceItem(
                l.getId(),
                "user_like",
                TYPE_LIKE,
                "点赞文物",
                l.getMuseumId(),
                l.getObjectId(),
                null,
                null,
                l.getCreatedAt()
        );
    }

    private static String normalizeType(String type) {
        return StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static boolean includeType(String normalizedType, String target) {
        return normalizedType == null || normalizedType.equals(target);
    }

    private Specification<Comment> buildCommentSpec(Long userId, LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("userId"), userId));
            if (from != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private Specification<UserUploadPhoto> buildPhotoSpec(Long userId, LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("userId"), userId));
            if (from != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private Specification<UserFavorite> buildFavoriteSpec(Long userId, LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("userId"), userId));
            if (from != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private Specification<UserLike> buildLikeSpec(Long userId, LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("userId"), userId));
            if (from != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
