package com.zynee.zynee.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zynee.zynee.model.MoodLog;
import com.zynee.zynee.model.User;

public interface MoodLogRepository extends JpaRepository<MoodLog, Long> {
    List<MoodLog> findByUser(User user);
    List<MoodLog> findByUserAndTimestampBetween(User user, LocalDateTime start, LocalDateTime end);
    List<MoodLog> findByUserEmail(String email);
    void deleteByUser(User user);

}
