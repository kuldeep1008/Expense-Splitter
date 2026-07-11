package com.example.expensesplitter.controller;

import com.example.expensesplitter.dto.CreateGroupRequest;
import com.example.expensesplitter.model.Group;
import com.example.expensesplitter.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<Group> createGroup(@Valid @RequestBody CreateGroupRequest request,
                                              Authentication authentication) {
        Group group = groupService.createGroup(request, authentication.getName());
        return ResponseEntity.ok(group);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<Group> getGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getGroupOrThrow(groupId));
    }
}
