package com.wherelee.etry.web;

import com.wherelee.etry.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面路由。所有页面都是 Thymeleaf 渲染，数据靠页面里的 JS 调 /api 拿。
 */
@Controller
@RequiredArgsConstructor
public class PageController {

    private final UserService userService;

    @GetMapping("/")
    public String home() {
        return "redirect:/files";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/verify")
    public String verify() {
        return "verify";
    }

    @GetMapping("/forgot")
    public String forgot() {
        return "forgot";
    }

    @GetMapping("/reset")
    public String reset() {
        return "reset";
    }

    @GetMapping("/files")
    public String files(@AuthenticationPrincipal Long userId, Model model) {
        model.addAttribute("user", userService.view(userService.get(userId)));
        return "files";
    }

    @GetMapping("/trash")
    public String trash(@AuthenticationPrincipal Long userId, Model model) {
        model.addAttribute("user", userService.view(userService.get(userId)));
        return "trash";
    }

    @GetMapping("/shares")
    public String shares(@AuthenticationPrincipal Long userId, Model model) {
        model.addAttribute("user", userService.view(userService.get(userId)));
        return "shares";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal Long userId, Model model) {
        model.addAttribute("user", userService.view(userService.get(userId)));
        return "profile";
    }
}
