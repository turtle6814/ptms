package com.example.backend.base;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public abstract class BaseService<T, ID> {

    private final JpaRepository<T, ID> repository;

    protected BaseService(JpaRepository<T, ID> repository) {
        this.repository = repository;
    }

    protected Optional<T> findById(ID id) {
        return repository.findById(id);
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
