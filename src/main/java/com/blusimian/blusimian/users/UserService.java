package com.blusimian.blusimian.users;

import com.blusimian.blusimian.users.exception.UserNotFoundException;
import com.blusimian.blusimian.users.exception.UsernameTakenException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Business logic for the current tenant's users. */
@Service
public class UserService {

    private static final String USERNAME_CONSTRAINT = "uq_users_username";

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return repository.findAllByOrderByUsernameAsc();
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return repository.findById(id).orElseThrow(UserNotFoundException::new);
    }

    /**
     * A new user in the current tenant. Usernames are unique across all tenants, which a
     * check under RLS can't see, so the unique violation is translated instead.
     */
    @Transactional
    public User create(String username, String displayName, String password) {
        User user = User.create(username, displayName, passwordEncoder.encode(password));
        try {
            return repository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException violation
                    && USERNAME_CONSTRAINT.equals(violation.getConstraintName())) {
                throw new UsernameTakenException(username);
            }
            throw e;
        }
    }

    @Transactional
    public User rename(UUID id, String displayName) {
        User user = repository.findById(id).orElseThrow(UserNotFoundException::new);
        user.rename(displayName.strip());
        return repository.saveAndFlush(user);
    }
}
