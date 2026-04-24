package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.RoleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleDefinitionRepository extends JpaRepository<RoleDefinition, Long> {
    Optional<RoleDefinition> findByCode(String code);
}
