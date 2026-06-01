package com.buct.adminbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "artifact")
public class Artifact {

    @EmbeddedId
    private ArtifactId id = new ArtifactId();

    @Column(name = "artifact_id", nullable = false, length = 256)
    private String artifactId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 500)
    private String artist;

    @Column(name = "artist_province", length = 100)
    private String artistProvince;

    @Column(length = 200)
    private String dynasty;

    @Column(name = "artist_wikidata_id", nullable = false, length = 32)
    private String artistWikidataId = "";

    @Column(name = "artist_birth", nullable = false, length = 120)
    private String artistBirth = "";

    @Column(name = "artist_death", nullable = false, length = 120)
    private String artistDeath = "";

    @Column(name = "artist_bio", nullable = false, length = 4000)
    private String artistBio = "";

    @Column(name = "artist_wikipedia_summary", nullable = false, length = 4000)
    private String artistWikipediaSummary = "";

    @Column(name = "artist_enriched_at", nullable = false, length = 32)
    private String artistEnrichedAt = "";

    @Column(nullable = false, length = 200)
    private String period;

    @Column(name = "period_start_year")
    private Short periodStartYear;

    @Column(name = "period_end_year")
    private Short periodEndYear;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String material;

    @Column(length = 300)
    private String culture;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String provenance;

    @Column(columnDefinition = "TEXT")
    private String bibliography;

    @Column(columnDefinition = "TEXT")
    private String dimensions;

    @Column(nullable = false, length = 300)
    private String museum;

    @Column(nullable = false, length = 300)
    private String location;

    @Column(name = "detail_url", nullable = false, columnDefinition = "TEXT")
    private String detailUrl;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    @Column(name = "iiif_manifest_url", columnDefinition = "TEXT")
    private String iiifManifestUrl;

    @Column(name = "image_path", nullable = false, columnDefinition = "TEXT")
    private String imagePath;

    @Column(name = "image_paths", columnDefinition = "TEXT")
    private String imagePaths;

    @Column(name = "image_count", nullable = false)
    private Short imageCount = 0;

    @Column(name = "credit_line", columnDefinition = "TEXT")
    private String creditLine;

    @Column(name = "accession_number", length = 200)
    private String accessionNumber;

    @Column(name = "crawl_date", nullable = false)
    private LocalDate crawlDate;

    /** 列表缩略图展示用（哈佛/MFA 走爬虫组图片 API，不落库） */
    @Transient
    private String displayImageUrl;

    @JsonProperty("id")
    public String getApiId() {
        return artifactId;
    }

    @JsonProperty("name")
    public String getName() {
        return title;
    }

    public void setName(String name) {
        this.title = name;
    }

    @JsonProperty("museumId")
    public Integer getMuseumId() {
        return id == null ? null : id.getMuseumId();
    }

    public void setMuseumId(Integer museumId) {
        if (id == null) {
            id = new ArtifactId();
        }
        id.setMuseumId(museumId);
    }

    @JsonProperty("objectId")
    public String getObjectId() {
        return id == null ? null : id.getObjectId();
    }

    public void setObjectId(String objectId) {
        if (id == null) {
            id = new ArtifactId();
        }
        id.setObjectId(objectId);
    }

    /** 前端兼容字段：由馆别推断来源 */
    @JsonProperty("sourceSystem")
    public String getSourceSystem() {
        Integer museumId = getMuseumId();
        if (museumId == null) {
            return "";
        }
        return switch (museumId) {
            case 1 -> "smithsonian";
            case 2 -> "harvard";
            case 3 -> "mfa";
            default -> "museum-" + museumId;
        };
    }
}
