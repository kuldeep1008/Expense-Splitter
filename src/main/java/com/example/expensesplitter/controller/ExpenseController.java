package com.example.expensesplitter.controller;

import com.example.expensesplitter.dto.AddExpenseRequest;
import com.example.expensesplitter.model.Expense;
import com.example.expensesplitter.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<Expense> addExpense(@PathVariable Long groupId,
                                               @Valid @RequestBody AddExpenseRequest request,
                                               Authentication authentication) {
        Expense expense = expenseService.addExpense(groupId, request, authentication.getName());
        return ResponseEntity.ok(expense);
    }

    @GetMapping
    public ResponseEntity<List<Expense>> listExpenses(@PathVariable Long groupId) {
        return ResponseEntity.ok(expenseService.getGroupExpenses(groupId));
    }
}
