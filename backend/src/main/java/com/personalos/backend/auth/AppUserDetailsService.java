package com.personalos.backend.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(email.trim().toLowerCase())
                .map(u -> new AppUserDetails(u.getId(), u.getEmail(), u.getPasswordHash(), u.isEmailVerified()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
