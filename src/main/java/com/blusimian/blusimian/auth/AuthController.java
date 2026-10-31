package com.blusimian.blusimian.auth;

import com.blusimian.blusimian.auth.dto.CurrentUserResponse;
import com.blusimian.blusimian.auth.dto.LoginRequest;
import com.blusimian.blusimian.platform.security.TenantUserPrincipal;
import com.blusimian.blusimian.tenants.Tenant;
import com.blusimian.blusimian.tenants.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and the current user. Logout is Spring Security's own filter (POST
 * /api/auth/logout). Login doesn't bind a tenant: TenantContextFilter does that, from the
 * session, on every later request.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TenantService tenants;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SessionAuthenticationStrategy sessionStrategy = new ChangeSessionIdAuthenticationStrategy();

    public AuthController(AuthenticationManager authenticationManager, TenantService tenants) {
        this.authenticationManager = authenticationManager;
        this.tenants = tenants;
    }

    // POST /api/auth/login -- 204 + session cookie; a failure is a 401 (GlobalExceptionHandler).
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void login(@Valid @RequestBody LoginRequest request,
                      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        // A session id from before the login must not carry over into the logged-in session.
        sessionStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }

    // GET /api/auth/me
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal TenantUserPrincipal principal) {
        Tenant tenant = tenants.current();
        return new CurrentUserResponse(principal.userId(), principal.getUsername(), principal.displayName(),
                new CurrentUserResponse.TenantSummary(tenant.getId(), tenant.getName()));
    }
}
