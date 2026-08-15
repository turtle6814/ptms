package com.example.backend.base;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.function.Function;

public abstract class BaseService<T, ID, RES> {

    private final JpaRepository<T, ID> repository;
    private final Function<T, RES> toResponse;
    private final String entityName;

    protected BaseService(JpaRepository<T, ID> repository, Function<T, RES> toResponse, String entityName) {
        this.repository = repository;
        this.toResponse = toResponse;
        this.entityName = entityName;
    }

    protected T findByIdOrThrow(ID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException(entityName + " not found"));
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
