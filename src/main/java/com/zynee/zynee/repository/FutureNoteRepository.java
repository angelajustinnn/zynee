package com.zynee.zynee.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zynee.zynee.model.FutureNote;
import com.zynee.zynee.model.User;

public interface FutureNoteRepository extends JpaRepository<FutureNote, Long> {

    List<FutureNote> findByUserOrderByUnlockAtAscCreatedAtAsc(User user);

    List<FutureNote> findByUserAndUnlockAtLessThanEqualAndOpenedAtIsNull(User user, LocalDateTime now);

    long countByUserAndUnlockAtLessThanEqualAndOpenedAtIsNull(User user, LocalDateTime now);

    List<FutureNote> findByUserEmail(String email);

    Optional<FutureNote> findByIdAndUser(Long id, User user);

    long deleteByIdAndUser_Id(Long id, Long userId);

    void deleteByUser(User user);
}
