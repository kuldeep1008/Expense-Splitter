package com.example.expensesplitter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "status", "running",
                "message", "Expense Splitter API is live",
                "docs", "See README on GitHub for full API documentation",
                "endpoints", List.of(
                        "POST /api/auth/register",
                        "POST /api/auth/login",
                        "POST /api/groups",
                        "POST /api/groups/{id}/expenses",
                        "GET  /api/groups/{id}/simplify-debts"
                )
        );
    }
}
