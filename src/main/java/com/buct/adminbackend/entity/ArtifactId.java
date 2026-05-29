package com.buct.adminbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ArtifactId implements Serializable {

    @Column(name = "museum_id", nullable = false)
    private Integer museumId;

    @Column(name = "object_id", nullable = false, length = 255)
    private String objectId;

    public ArtifactId(Integer museumId, String objectId) {
        this.museumId = museumId;
        this.objectId = objectId;
    }
}
