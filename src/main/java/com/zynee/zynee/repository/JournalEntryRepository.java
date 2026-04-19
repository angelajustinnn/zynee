package com.zynee.zynee.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zynee.zynee.model.JournalEntry;
import com.zynee.zynee.model.User;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findByUserAndDateBetween(User user, LocalDate start, LocalDate end);
    List<JournalEntry> findByUserOrderByDateDescTimeDesc(User user);
    List<JournalEntry> findByUserEmail(String email);
    void deleteByUser(User user);

}
