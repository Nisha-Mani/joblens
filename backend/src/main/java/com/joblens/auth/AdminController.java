package com.joblens.auth;

import com.joblens.user.UserRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only operational endpoints; access is enforced in SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;

    public AdminController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return Map.of("users", users.count());
    }
}
