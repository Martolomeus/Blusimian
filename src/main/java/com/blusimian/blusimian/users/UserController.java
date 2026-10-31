package com.blusimian.blusimian.users;

import com.blusimian.blusimian.users.dto.CreateUserRequest;
import com.blusimian.blusimian.users.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP layer for the caller's tenant's users. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    // GET /api/users
    @GetMapping
    public List<UserResponse> list() {
        return service.list().stream().map(UserResponse::from).toList();
    }

    // POST /api/users -- 409 if the username is taken in any tenant.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(service.create(request.username(), request.displayName(), request.password()));
    }
}
