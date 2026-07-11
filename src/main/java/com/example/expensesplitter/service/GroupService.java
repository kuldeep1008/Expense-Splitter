package com.example.expensesplitter.service;

import com.example.expensesplitter.dto.CreateGroupRequest;
import com.example.expensesplitter.exception.ApiException;
import com.example.expensesplitter.model.Group;
import com.example.expensesplitter.model.GroupMember;
import com.example.expensesplitter.model.User;
import com.example.expensesplitter.repository.GroupMemberRepository;
import com.example.expensesplitter.repository.GroupRepository;
import com.example.expensesplitter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public Group createGroup(CreateGroupRequest request, String creatorEmail) {
        User creator = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));

        Group group = Group.builder()
                .name(request.getName())
                .createdBy(creator)
                .build();
        group = groupRepository.save(group);

        addMember(group, creator);

        for (String email : request.getMemberEmails()) {
            User member = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No user with email: " + email));
            if (!member.getId().equals(creator.getId())) {
                addMember(group, member);
            }
        }

        return group;
    }

    private void addMember(Group group, User user) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(group.getId(), user.getId())) {
            groupMemberRepository.save(GroupMember.builder().group(group).user(user).build());
        }
    }

    public Group getGroupOrThrow(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Group not found: " + groupId));
    }
}
