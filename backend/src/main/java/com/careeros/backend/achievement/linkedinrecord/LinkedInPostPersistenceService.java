package com.careeros.backend.achievement.linkedinrecord;

import com.careeros.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LinkedInPostPersistenceService {

    private final LinkedInPostRepository repository;

    public Optional<LinkedInPostEntity> findByUser(User user) {
        return repository.findByUser(user);
    }

    public LinkedInPostEntity save(LinkedInPostEntity entity) {
        return repository.save(entity);
    }
}