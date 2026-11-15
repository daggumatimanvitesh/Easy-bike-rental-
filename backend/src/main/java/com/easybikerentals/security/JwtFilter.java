package com.easybikerentals.security;

import com.easybikerentals.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {
 private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);
 private final JwtService jwt;
 private final UserRepository users;

 public JwtFilter(JwtService jwt, UserRepository users) {
  this.jwt = jwt;
  this.users = users;
 }

 @Override
 protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
   throws ServletException, IOException {
  // Do not allow authentication from a previous request to leak into this one.
  SecurityContextHolder.clearContext();
  String header = request.getHeader("Authorization");
  if (header == null || !header.startsWith("Bearer ")) {
   request.setAttribute("authFailure", "MISSING_BEARER_TOKEN");
   if (request.getRequestURI().startsWith("/api/bookings") || request.getRequestURI().startsWith("/api/payments/")) {
    log.warn("Protected request {} {} arrived without a bearer token", request.getMethod(), request.getRequestURI());
   }
  } else {
   try {
    String email = jwt.email(header.substring(7));
    var user = users.findByEmail(email).orElse(null);
    if (user == null) {
     request.setAttribute("authFailure", "TOKEN_USER_NOT_FOUND");
     log.warn("Rejected {} {} because token subject is not a current user", request.getMethod(), request.getRequestURI());
    } else if (!user.active) {
     request.setAttribute("authFailure", "USER_DISABLED");
     log.warn("Rejected {} {} because the account is disabled", request.getMethod(), request.getRequestURI());
    } else {
     SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
       email, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role))));
    }
   } catch (Exception exception) {
    request.setAttribute("authFailure", "TOKEN_REJECTED");
    log.warn("Rejected bearer token for {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
   }
  }
  chain.doFilter(request, response);
 }
}
