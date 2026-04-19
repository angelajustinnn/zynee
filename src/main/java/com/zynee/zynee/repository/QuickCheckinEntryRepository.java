package com.zynee.zynee.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zynee.zynee.model.QuickCheckinEntry;
import com.zynee.zynee.model.User;

public interface QuickCheckinEntryRepository extends JpaRepository<QuickCheckinEntry, Long> {
    Optional<QuickCheckinEntry> findTopByUserOrderByCreatedAtDesc(User user);
    List<QuickCheckinEntry> findTop20ByUserOrderByCreatedAtDesc(User user);
    List<QuickCheckinEntry> findByUserAndCreatedAtBetweenOrderByCreatedAtAsc(
            User user,
            LocalDateTime start,
            LocalDateTime end);
    List<QuickCheckinEntry> findByUserEmail(String email);
    void deleteByUser(User user);
}
