package com.example.backend.base;

import com.example.backend.enums.Role;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.NotFoundException;
import com.example.backend.tournament.entity.Tournament;
import com.example.backend.user.repository.UserRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.function.Function;

public abstract class BaseService<T, ID, RES> {

    private final JpaRepository<T, ID> repository;
    private final Function<T, RES> toResponse;
    private final String entityName;
    private final UserRepository userRepository;

    protected BaseService(JpaRepository<T, ID> repository, Function<T, RES> toResponse, String entityName,
                           UserRepository userRepository) {
        this.repository = repository;
        this.toResponse = toResponse;
        this.entityName = entityName;
        this.userRepository = userRepository;
    }

    protected T findByIdOrThrow(ID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException(entityName + " not found"));
    }

    protected boolean isAdmin(String username) {
        return userRepository.findByUsername(username)
                .map(user -> user.getRole() == Role.ADMIN)
                .orElse(false);
    }

    protected void verifyOwnership(Tournament tournament, String username) {
        if (isAdmin(username)) {
            return;
        }
        if (tournament.getOwner() == null || !tournament.getOwner().getUsername().equals(username)) {
            throw new ForbiddenException("You do not have permission to perform this action");
        }
    }

    public RES getById(ID id) {
        return toResponse.apply(findByIdOrThrow(id));
    }

    protected List<T> findAll() {
        return repository.findAll();
    }

    protected T save(T entity) {
        return repository.save(entity);
    }

    protected void deleteById(ID id) {
        repository.deleteById(id);
    }
}
