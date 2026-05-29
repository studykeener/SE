package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.Artifact;
import com.buct.adminbackend.entity.ArtifactId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtifactRepository extends JpaRepository<Artifact, ArtifactId> {

    Optional<Artifact> findByArtifactId(String artifactId);
}
