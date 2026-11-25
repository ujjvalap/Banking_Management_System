package com.sprint.bank.controller;

import com.sprint.bank.security.principal.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("authenticated", principal != null);
        if (principal != null) {
            boolean isAdmin = principal.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            model.addAttribute("dashboardUrl", isAdmin ? "/admin/dashboard" : "/customer/dashboard");
        }
        return "index";
    }

    @GetMapping({"/auth/login", "/login"})
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping({"/auth/register", "/register"})
    public String registerPage() {
        return "auth/register";
    }

    @GetMapping({"/auth/forgot-password", "/forgot-password"})
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @GetMapping({"/auth/reset-password", "/reset-password"})
    public String resetPasswordPage() {
        return "auth/reset-password";
    }

    @GetMapping({"/auth/verify-email", "/verify-email"})
    public String verifyEmailPage() {
        return "auth/verify-email";
    }
}
