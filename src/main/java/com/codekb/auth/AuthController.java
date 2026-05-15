package com.codekb.auth;

import com.codekb.common.ApiResponse;
import com.codekb.common.BusinessException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResult> login(@RequestBody LoginRequest req) {
        if (req == null || req.username == null || req.password == null) {
            throw new BusinessException(400, "用户名和密码必填");
        }
        return ApiResponse.ok(authService.login(req.username, req.password));
    }

    @GetMapping("/me")
    public ApiResponse<AuthService.UserView> me(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(401, "未登录");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CodeKbPrincipal p) {
            User u = authService.loadById(p.userId());
            return ApiResponse.ok(AuthService.UserView.from(u));
        }
        throw new BusinessException(401, "未登录");
    }

    public static class LoginRequest {
        @NotBlank
        public String username;
        @NotBlank
        public String password;
    }
}
