package com.zynee.zynee.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zynee.zynee.model.Affirmation;
import com.zynee.zynee.model.SavedAffirmation;
import com.zynee.zynee.model.User;

public interface SavedAffirmationRepository extends JpaRepository<SavedAffirmation, Long> {
    
    List<SavedAffirmation> findByUser(User user);
    List<SavedAffirmation> findByUserEmail(String email);
    boolean existsByUserAndAffirmation(User user, Affirmation affirmation);

    void deleteByUser(User user);
}
