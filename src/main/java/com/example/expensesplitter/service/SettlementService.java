package com.example.expensesplitter.service;

import com.example.expensesplitter.dto.RecordSettlementRequest;
import com.example.expensesplitter.exception.ApiException;
import com.example.expensesplitter.model.Group;
import com.example.expensesplitter.model.Settlement;
import com.example.expensesplitter.model.User;
import com.example.expensesplitter.repository.SettlementRepository;
import com.example.expensesplitter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;

    public Settlement recordSettlement(Long groupId, RecordSettlementRequest request) {
        Group group = groupService.getGroupOrThrow(groupId);

        User payer = userRepository.findById(request.getPayerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payer not found"));
        User payee = userRepository.findById(request.getPayeeId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payee not found"));

        Settlement settlement = Settlement.builder()
                .group(group)
                .payer(payer)
                .payee(payee)
                .amount(request.getAmount())
                .build();

        return settlementRepository.save(settlement);
    }
}
