package com.zynee.zynee.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zynee.zynee.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

}
