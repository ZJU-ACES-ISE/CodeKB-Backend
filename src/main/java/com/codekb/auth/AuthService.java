package com.codekb.auth;

import com.codekb.common.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResult login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        String token = jwtService.generateToken(user);
        return new LoginResult(token, UserView.from(user));
    }

    public User loadById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
    }

    public record LoginResult(String token, UserView user) {
    }

    public record UserView(Long id, String username, String displayName, String role) {
        public static UserView from(User u) {
            return new UserView(u.getId(), u.getUsername(),
                    u.getDisplayName() == null ? u.getUsername() : u.getDisplayName(),
                    u.getRole().name());
        }
    }
}
