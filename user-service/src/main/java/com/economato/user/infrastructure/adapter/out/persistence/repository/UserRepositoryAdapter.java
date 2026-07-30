package com.economato.user.infrastructure.adapter.out.persistence.repository;

import com.economato.user.application.port.out.UserRepositoryPort;
import com.economato.user.domain.model.Role;
import com.economato.user.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final JpaUserRepository jpaUserRepository;

    public UserRepositoryAdapter(JpaUserRepository jpaUserRepository) {
        this.jpaUserRepository = jpaUserRepository;
    }

    @Override
    public Optional<User> findById(Integer id) {
        return jpaUserRepository.findById(id);
    }

    @Override
    public Optional<User> findByName(String name) {
        return jpaUserRepository.findByName(name);
    }

    @Override
    public Optional<User> findByUser(String user) {
        return jpaUserRepository.findByUser(user);
    }

    @Override
    public Page<User> findAll(Pageable pageable) {
        return jpaUserRepository.findAll(pageable);
    }

    @Override
    public List<User> findByRole(Role role) {
        return jpaUserRepository.findByRole(role);
    }

    @Override
    public Page<User> findByRoleIn(List<Role> roles, Pageable pageable) {
        return jpaUserRepository.findByRoleIn(roles, pageable);
    }

    @Override
    public User save(User user) {
        return jpaUserRepository.save(user);
    }

    @Override
    public void deleteById(Integer id) {
        jpaUserRepository.deleteById(id);
    }

    @Override
    public boolean existsByUser(String user) {
        return jpaUserRepository.existsByUser(user);
    }

    @Override
    public List<User> findByTeacherId(Integer teacherId) {
        return jpaUserRepository.findByTeacherId(teacherId);
    }

    @Override
    public Page<User> findByIsHidden(boolean isHidden, Pageable pageable) {
        return isHidden ? jpaUserRepository.findByIsHiddenTrue(pageable) : jpaUserRepository.findByIsHiddenFalse(pageable);
    }

    @Override
    public long countByRole(Role role) {
        return jpaUserRepository.countByRole(role);
    }

    @Override
    public long countByIsHidden(boolean isHidden) {
        return jpaUserRepository.countByIsHidden(isHidden);
    }
}
