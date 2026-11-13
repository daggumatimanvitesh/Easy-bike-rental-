package com.easybikerentals.security;
import com.easybikerentals.repository.UserRepository; import jakarta.servlet.*; import jakarta.servlet.http.*; import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter; import java.io.IOException; import java.util.List;
@Component public class JwtFilter extends OncePerRequestFilter {
 private final JwtService jwt; private final UserRepository users; public JwtFilter(JwtService j,UserRepository u){jwt=j;users=u;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String header=request.getHeader("Authorization"); if(header!=null&&header.startsWith("Bearer "))try{var email=jwt.email(header.substring(7));var user=users.findByEmail(email).orElse(null);if(user!=null&&user.active)SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role))));}catch(Exception ignored){}
  chain.doFilter(request,response);
 }
}
