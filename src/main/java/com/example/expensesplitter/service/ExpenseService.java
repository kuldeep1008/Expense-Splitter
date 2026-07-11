package com.example.expensesplitter.service;

import com.example.expensesplitter.dto.AddExpenseRequest;
import com.example.expensesplitter.exception.ApiException;
import com.example.expensesplitter.model.Expense;
import com.example.expensesplitter.model.ExpenseShare;
import com.example.expensesplitter.model.Group;
import com.example.expensesplitter.model.User;
import com.example.expensesplitter.repository.ExpenseRepository;
import com.example.expensesplitter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final AiCategorizationService aiCategorizationService;

    @Transactional
    public Expense addExpense(Long groupId, AddExpenseRequest request, String payerEmail) {
        Group group = groupService.getGroupOrThrow(groupId);

        User payer = userRepository.findByEmail(payerEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(payer)
                .description(request.getDescription())
                .amount(request.getAmount())
                .category(aiCategorizationService.categorize(request.getDescription()))
                .build();

        List<ExpenseShare> shares = buildShares(expense, request);
        expense.setShares(shares);

        return expenseRepository.save(expense);
    }

    private List<ExpenseShare> buildShares(Expense expense, AddExpenseRequest request) {
        List<ExpenseShare> shares = new ArrayList<>();
        BigDecimal amount = request.getAmount();

        switch (request.getSplitType()) {
            case EQUAL -> {
                List<Long> participants = request.getParticipantUserIds();
                if (participants == null || participants.isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "participantUserIds required for EQUAL split");
                }
                BigDecimal perHead = amount.divide(BigDecimal.valueOf(participants.size()), 2, RoundingMode.HALF_UP);
                // Give any rounding remainder to the first participant so shares always sum to the total exactly.
                BigDecimal runningTotal = BigDecimal.ZERO;
                for (int i = 0; i < participants.size(); i++) {
                    User u = userRepository.findById(participants.get(i))
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
                    BigDecimal share = (i == participants.size() - 1)
                            ? amount.subtract(runningTotal)
                            : perHead;
                    runningTotal = runningTotal.add(share);
                    shares.add(ExpenseShare.builder().expense(expense).user(u).shareAmount(share).build());
                }
            }
            case EXACT -> {
                validateShares(request);
                BigDecimal sum = BigDecimal.ZERO;
                for (var s : request.getShares()) {
                    User u = userRepository.findById(s.getUserId())
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
                    shares.add(ExpenseShare.builder().expense(expense).user(u).shareAmount(s.getValue()).build());
                    sum = sum.add(s.getValue());
                }
                if (sum.compareTo(amount) != 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "EXACT shares must sum to the total amount (" + amount + "), got " + sum);
                }
            }
            case PERCENTAGE -> {
                validateShares(request);
                BigDecimal percentSum = BigDecimal.ZERO;
                for (var s : request.getShares()) {
                    percentSum = percentSum.add(s.getValue());
                }
                if (percentSum.compareTo(BigDecimal.valueOf(100)) != 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Percentages must sum to 100, got " + percentSum);
                }
                for (var s : request.getShares()) {
                    User u = userRepository.findById(s.getUserId())
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
                    BigDecimal share = amount.multiply(s.getValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    shares.add(ExpenseShare.builder().expense(expense).user(u).shareAmount(share).build());
                }
            }
        }
        return shares;
    }

    private void validateShares(AddExpenseRequest request) {
        if (request.getShares() == null || request.getShares().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "shares required for EXACT/PERCENTAGE split");
        }
    }

    public List<Expense> getGroupExpenses(Long groupId) {
        return expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    }
}
