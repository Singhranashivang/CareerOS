package com.careeros.backend.common;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication) {

        if (authentication == null) {
            return "Welcome to CareerOS";
        }

        return "Hello " + authentication.getName();
    }
}