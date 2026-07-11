package com.example.expensesplitter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateGroupRequest {
    @NotBlank
    private String name;

    // Emails of members to add besides the creator
    @NotEmpty
    private List<String> memberEmails;
}
