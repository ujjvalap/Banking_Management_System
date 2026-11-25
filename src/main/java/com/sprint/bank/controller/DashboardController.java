package com.sprint.bank.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/customer/dashboard")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String customerDashboard(Model model) {
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("portal", "customer");
        return "customer/dashboard";
    }

    @GetMapping("/customer/accounts")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String customerAccounts(Model model) {
        model.addAttribute("activePage", "accounts");
        model.addAttribute("portal", "customer");
        return "customer/accounts";
    }

    @GetMapping("/customer/open-account")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String openAccount(Model model) {
        model.addAttribute("activePage", "open-account");
        model.addAttribute("portal", "customer");
        return "customer/open-account";
    }

    @GetMapping("/customer/deposit")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String deposit(Model model) {
        model.addAttribute("activePage", "deposit");
        model.addAttribute("portal", "customer");
        return "customer/deposit";
    }

    @GetMapping("/customer/withdraw")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String withdraw(Model model) {
        model.addAttribute("activePage", "withdraw");
        model.addAttribute("portal", "customer");
        return "customer/withdraw";
    }

    @GetMapping("/customer/transfer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String transfer(Model model) {
        model.addAttribute("activePage", "transfer");
        model.addAttribute("portal", "customer");
        return "customer/transfer";
    }

    @GetMapping("/customer/statements")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String statements(Model model) {
        model.addAttribute("activePage", "statements");
        model.addAttribute("portal", "customer");
        return "customer/statements";
    }

    @GetMapping("/customer/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String profile(Model model) {
        model.addAttribute("activePage", "profile");
        model.addAttribute("portal", "customer");
        return "customer/profile";
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminDashboard(Model model) {
        model.addAttribute("activePage", "admin-dashboard");
        model.addAttribute("portal", "admin");
        return "admin/dashboard";
    }

    @GetMapping("/admin/customers")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminCustomers(Model model) {
        model.addAttribute("activePage", "admin-customers");
        model.addAttribute("portal", "admin");
        return "admin/customers";
    }

    @GetMapping("/admin/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminAccounts(Model model) {
        model.addAttribute("activePage", "admin-accounts");
        model.addAttribute("portal", "admin");
        return "admin/accounts";
    }

    @GetMapping("/admin/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminTransactions(Model model) {
        model.addAttribute("activePage", "admin-transactions");
        model.addAttribute("portal", "admin");
        return "admin/transactions";
    }
}
