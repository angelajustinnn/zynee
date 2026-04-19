package com.zynee.zynee.controller;

import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zynee.zynee.model.Affirmation;
import com.zynee.zynee.repository.AffirmationRepository;

@RestController
@RequestMapping("/api/affirmation")
public class AffirmationController {

    @Autowired
    private AffirmationRepository affirmationRepository;

    @GetMapping("/random")
public ResponseEntity<Affirmation> getRandomAffirmation() {
    List<Affirmation> affirmations = affirmationRepository.findAll();
    if (affirmations.isEmpty()) {
        return ResponseEntity.status(404).build();
    }
    Random rand = new Random();
    Affirmation randomAffirmation = affirmations.get(rand.nextInt(affirmations.size()));
    return ResponseEntity.ok(randomAffirmation); // ✅ return whole object
}
}
