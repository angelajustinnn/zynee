package com.zynee.zynee.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InsightsController {

    @GetMapping({"/insights", "/insights.html"})
    public String insightsPage() {
        return "insights.html";
    }
}
