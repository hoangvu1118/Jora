package com.sefujo.common.security;

import com.sefujo.user.User;
import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data
public class CustomUserDetail implements UserDetails {

    @Id
    private long id;
    private String email;
    private String password;
    private String role;
    private Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetail(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.password = user.getPassword();
        this.authorities = List.of(
                new SimpleGrantedAuthority(user.getRole() != null ? "ROLE_" + user.getRole().toUpperCase() : "ROLE_USER")
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

}
