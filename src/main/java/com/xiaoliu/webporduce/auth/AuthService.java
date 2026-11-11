package com.xiaoliu.webporduce.auth;

import com.xiaoliu.webporduce.common.ApiException;
import com.xiaoliu.webporduce.project.DemoDataService;
import com.xiaoliu.webporduce.user.UserAccount;
import com.xiaoliu.webporduce.user.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final DemoDataService demoDataService;
    private final boolean seedDemo;

    public AuthService(UserAccountRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       DemoDataService demoDataService,
                       @Value("${app.demo.seed-on-register:true}") boolean seedDemo) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.demoDataService = demoDataService;
        this.seedDemo = seedDemo;
    }

    @Transactional
    public UserAccount register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "该邮箱已经注册");
        }

        String displayName = request.displayName().trim();
        UserAccount user = new UserAccount(email, passwordEncoder.encode(request.password()), displayName);
        userRepository.save(user);

        if (seedDemo) {
            demoDataService.seed(user);
        }
        return user;
    }

    public org.springframework.security.core.Authentication authenticate(String email, String password) {
        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(email), password));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
