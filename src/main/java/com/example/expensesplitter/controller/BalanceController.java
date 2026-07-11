package com.example.expensesplitter.controller;

import com.example.expensesplitter.dto.RecordSettlementRequest;
import com.example.expensesplitter.dto.SettlementSuggestion;
import com.example.expensesplitter.model.Settlement;
import com.example.expensesplitter.repository.GroupMemberRepository;
import com.example.expensesplitter.service.BalanceService;
import com.example.expensesplitter.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;
    private final SettlementService settlementService;
    private final GroupMemberRepository groupMemberRepository;

    @GetMapping("/balances")
    public ResponseEntity<Map<Long, BigDecimal>> getBalances(@PathVariable Long groupId) {
        return ResponseEntity.ok(balanceService.computeNetBalances(groupId));
    }

    // The headline feature: minimum set of transactions to settle everyone up.
    @GetMapping("/simplify-debts")
    public ResponseEntity<List<SettlementSuggestion>> simplifyDebts(@PathVariable Long groupId) {
        Map<Long, String> userNames = groupMemberRepository.findByGroupId(groupId).stream()
                .collect(Collectors.toMap(gm -> gm.getUser().getId(), gm -> gm.getUser().getName()));
        return ResponseEntity.ok(balanceService.simplifyDebts(groupId, userNames));
    }

    @PostMapping("/settlements")
    public ResponseEntity<Settlement> recordSettlement(@PathVariable Long groupId,
                                                        @RequestBody RecordSettlementRequest request) {
        return ResponseEntity.ok(settlementService.recordSettlement(groupId, request));
    }
}
