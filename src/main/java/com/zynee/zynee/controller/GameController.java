package com.zynee.zynee.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zynee.zynee.model.User;
import com.zynee.zynee.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/game")
public class GameController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/get-streak")
public Map<String, Object> getStreak(HttpSession session) {
    Map<String, Object> response = new HashMap<>();
    Long userId = (Long) session.getAttribute("userId");

    if (userId == null) {
        response.put("success", false);
        response.put("message", "User not logged in.");
        return response;
    }

    User user = userRepository.findById(userId).orElse(null);
    if (user == null) {
        response.put("success", false);
        response.put("message", "User not found.");
        return response;
    }

    response.put("success", true);
    response.put("highestStreak", user.getRpsMaxStreak());
    return response;
}

    @PostMapping("/update-streak")
    public Map<String, Object> updateStreak(@RequestParam("streak") int newStreak, HttpSession session) {
        Map<String, Object> response = new HashMap<>();

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            response.put("success", false);
            response.put("message", "User not logged in.");
            return response;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            response.put("success", false);
            response.put("message", "User not found.");
            return response;
        }

        Integer storedStreak = user.getRpsMaxStreak();
int oldStreak = (storedStreak != null) ? storedStreak : 0;
        if (newStreak > oldStreak) {
            user.setRpsMaxStreak(newStreak);
            userRepository.save(user);
            response.put("newRecord", true);
        } else {
            response.put("newRecord", false);
        }

        response.put("success", true);
        response.put("highestStreak", user.getRpsMaxStreak());
        return response;
    }
}
