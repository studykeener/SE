package com.buct.adminbackend.service;

import com.buct.adminbackend.config.ArtifactImageApiProperties;
import com.buct.adminbackend.entity.Artifact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArtifactImageUrlResolver {

    private final ArtifactImageApiProperties properties;

    public void enrichDisplayImageUrl(Artifact artifact) {
        if (artifact == null) {
            return;
        }
        artifact.setDisplayImageUrl(resolve(artifact));
    }

    public String resolve(Artifact artifact) {
        if (artifact == null) {
            return "";
        }
        return resolve(artifact.getMuseumId(), artifact.getObjectId(), artifact.getImageUrl());
    }

    public String resolve(Integer museumId, String objectId, String imageUrl) {
        if (StringUtils.hasText(imageUrl) && imageUrl.startsWith("/uploads/")) {
            return imageUrl;
        }
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getBaseUrl())) {
            return StringUtils.hasText(imageUrl) ? imageUrl : "";
        }
        if (museumId == null || !StringUtils.hasText(objectId)) {
            return StringUtils.hasText(imageUrl) ? imageUrl : "";
        }
        List<Integer> proxyIds = properties.getProxyMuseumIds();
        if (proxyIds == null || !proxyIds.contains(museumId)) {
            return StringUtils.hasText(imageUrl) ? imageUrl : "";
        }
        String base = properties.getBaseUrl().trim().replaceAll("/+$", "");
        String encodedOid = UriUtils.encodePathSegment(objectId.trim(), StandardCharsets.UTF_8);
        return base + "/api/img/" + museumId + "/" + encodedOid;
    }
}
