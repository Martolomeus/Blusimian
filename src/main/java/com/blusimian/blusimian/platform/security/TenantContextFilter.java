package com.blusimian.blusimian.platform.security;

import com.blusimian.blusimian.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds the authenticated principal's tenant for the rest of the request: the only place
 * an HTTP request gets a tenant. Never from a header, path or body. Not a @Component, or
 * Boot would also register it as a plain servlet filter and it would run twice.
 */
class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            TenantContext.callAs(principal.tenantId(), () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);   // unreachable: doFilter throws nothing else
        }
    }
}
