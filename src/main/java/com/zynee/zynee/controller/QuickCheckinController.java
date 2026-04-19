package com.zynee.zynee.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class QuickCheckinController {

    @GetMapping({"/quick-checkin", "/quick-checkin.html"})
    public String quickCheckinPage() {
        return "quick-checkin.html";
    }
}
