package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.SensitiveWord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, Long> {
    List<SensitiveWord> findByEnabledTrueOrderByWordAsc();

    boolean existsByWord(String word);
}
