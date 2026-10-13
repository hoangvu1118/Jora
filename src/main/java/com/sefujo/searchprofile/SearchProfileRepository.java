package com.sefujo.searchprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SearchProfileRepository extends JpaRepository<SearchProfile, Long> {
    Optional<SearchProfile> findByUserId(Long userId);

    boolean existsByUserId(long userId);
}
