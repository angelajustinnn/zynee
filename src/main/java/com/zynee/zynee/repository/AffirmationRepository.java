package com.zynee.zynee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zynee.zynee.model.Affirmation;

@Repository
public interface AffirmationRepository extends JpaRepository<Affirmation, Long> {
    Affirmation findByMessage(String message);  // ✅ No custom @Query here
}
