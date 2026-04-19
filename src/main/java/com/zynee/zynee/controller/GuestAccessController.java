package com.zynee.zynee.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.zynee.zynee.service.GuestSessionService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class GuestAccessController {

    private final GuestSessionService guestSessionService;

    public GuestAccessController(GuestSessionService guestSessionService) {
        this.guestSessionService = guestSessionService;
    }

    @GetMapping("/guest-access")
    public String continueAsGuestGet(HttpServletRequest request) {
        return startGuestSession(request);
    }

    @PostMapping("/guest-access")
    public String continueAsGuestPost(HttpServletRequest request) {
        return startGuestSession(request);
    }

    private String startGuestSession(HttpServletRequest request) {
        guestSessionService.startGuestSession(request);
        return "redirect:/home.html?guest=1";
    }
}
