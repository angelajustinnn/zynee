package com.zynee.zynee.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zynee.zynee.model.Affirmation;
import com.zynee.zynee.model.SavedAffirmation;
import com.zynee.zynee.model.User;
import com.zynee.zynee.repository.AffirmationRepository;
import com.zynee.zynee.repository.SavedAffirmationRepository;
import com.zynee.zynee.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/saved")
public class SavedAffirmationController {

    @Autowired
    private SavedAffirmationRepository savedAffirmationRepository;

    @Autowired
    private AffirmationRepository affirmationRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/save")
    public ResponseEntity<?> saveAffirmation(@RequestBody Affirmation incoming, HttpSession session) {
        System.out.println("✅ Save request received");

        Object userIdObj = session.getAttribute("userId");
        if (userIdObj == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not logged in");
        }

        Long userId = (Long) userIdObj;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        Affirmation matched = affirmationRepository.findById(incoming.getId()).orElse(null);
        if (matched == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Affirmation not found");
        }

        boolean alreadySaved = savedAffirmationRepository.existsByUserAndAffirmation(user, matched);
        if (alreadySaved) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Already saved");
        }

        SavedAffirmation saved = new SavedAffirmation();
        saved.setUser(user);
        saved.setAffirmation(matched);
        saved.setSavedAt(LocalDateTime.now());

        savedAffirmationRepository.save(saved);
        return ResponseEntity.ok("Saved");
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllSaved(HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        if (userIdObj == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of());
        }

        Long userId = (Long) userIdObj;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(List.of());
        }

        return ResponseEntity.ok(savedAffirmationRepository.findByUser(user));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteSavedAffirmation(@PathVariable Long id, HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        if (userIdObj == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not logged in");
        }

        Long userId = (Long) userIdObj;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        SavedAffirmation saved = savedAffirmationRepository.findById(id).orElse(null);
        if (saved == null || !saved.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or unauthorized delete");
        }

        savedAffirmationRepository.deleteById(id);
        return ResponseEntity.ok("Deleted");
    }
}
