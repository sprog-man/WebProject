package com.xiaoliu.webporduce.auth;

import com.xiaoliu.webporduce.common.ApiException;
import com.xiaoliu.webporduce.security.AppUserPrincipal;
import com.xiaoliu.webporduce.security.CurrentUser;
import com.xiaoliu.webporduce.user.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthService authService, SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        UserAccount user = authService.register(request);
        Authentication authentication = authenticateAndStore(request.email(), request.password(), httpRequest, httpResponse);
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user, principal));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        Authentication authentication = authenticateAndStore(request.email(), request.password(), httpRequest, httpResponse);
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return new UserResponse(principal.getId(), principal.getEmail(), principal.getDisplayName(), null);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me() {
        AppUserPrincipal principal = CurrentUser.require();
        return new UserResponse(principal.getId(), principal.getEmail(), principal.getDisplayName(), null);
    }

    private Authentication authenticateAndStore(String email, String password,
                                                HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authService.authenticate(email, password);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确");
        }
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录失败，请重试");
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return authentication;
    }

    private UserResponse toResponse(UserAccount user, AppUserPrincipal principal) {
        // The registration response is created before the transaction commits;
        // fall back to principal values if the entity timestamp is unavailable.
        java.time.Instant createdAt = user.getCreatedAt();
        return new UserResponse(principal.getId(), principal.getEmail(), principal.getDisplayName(), createdAt);
    }
}
