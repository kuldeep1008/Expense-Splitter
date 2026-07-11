package com.example.expensesplitter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AddExpenseRequest {

    @NotBlank
    private String description;

    @NotNull
    @Positive
    private BigDecimal amount;

    // How to split. EQUAL splits automatically among 'participantUserIds'.
    // EXACT/PERCENTAGE requires 'shares' to specify each user's cut.
    @NotNull
    private SplitType splitType;

    // Used when splitType = EQUAL
    private List<Long> participantUserIds;

    // Used when splitType = EXACT (amount per user) or PERCENTAGE (0-100 per user)
    private List<ShareInput> shares;

    public enum SplitType { EQUAL, EXACT, PERCENTAGE }

    @Data
    public static class ShareInput {
        private Long userId;
        private BigDecimal value;
    }
}
