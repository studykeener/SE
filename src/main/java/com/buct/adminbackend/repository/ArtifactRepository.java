package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.Artifact;
import com.buct.adminbackend.entity.ArtifactId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ArtifactRepository extends JpaRepository<Artifact, ArtifactId>, JpaSpecificationExecutor<Artifact> {

    Optional<Artifact> findByArtifactId(String artifactId);
}
