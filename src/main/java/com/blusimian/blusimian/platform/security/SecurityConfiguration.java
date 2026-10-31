package com.blusimian.blusimian.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal session login: every endpoint but login needs a session, and TenantContextFilter
 * binds the session's tenant behind the authorization check.
 */
@Configuration
class SecurityConfiguration {

    @Bean
    SecurityFilterChain api(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new ApiErrorAuthenticationEntryPoint(jsonMapper)))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/auth/logout"))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                // A JSON API has no page to return to after login, so a 401 needn't open a session.
                .requestCache(cache -> cache.disable())
                // Off until the secure-auth work; SameSite=Lax and JSON-only bodies cover dev.
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .addFilterAfter(new TenantContextFilter(), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();   // {bcrypt} today, upgradable
    }

    // Reports an unknown username as bad credentials, after a dummy hash, so neither the
    // response nor its timing tells which usernames exist.
    @Bean
    AuthenticationManager authenticationManager(TenantUserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
