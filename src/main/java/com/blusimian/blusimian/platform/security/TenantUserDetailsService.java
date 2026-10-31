package com.blusimian.blusimian.platform.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/** Loads a user for DaoAuthenticationProvider, from any tenant: the username is global. */
@Component
class TenantUserDetailsService implements UserDetailsService {

    private final LoginLookup lookup;

    TenantUserDetailsService(LoginLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return lookup.find(username).orElseThrow(() -> new UsernameNotFoundException("No such user"));
    }
}
