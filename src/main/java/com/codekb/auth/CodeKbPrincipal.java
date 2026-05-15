package com.codekb.auth;

public record CodeKbPrincipal(Long userId, String username, String role) {
}
