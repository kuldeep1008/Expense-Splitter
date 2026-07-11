package com.example.expensesplitter.repository;

import com.example.expensesplitter.model.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {
    List<ExpenseShare> findByExpenseGroupId(Long groupId);
}
