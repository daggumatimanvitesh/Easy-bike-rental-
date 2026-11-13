package com.easybikerentals.controller;
import com.easybikerentals.entity.User; import com.easybikerentals.repository.UserRepository; import com.easybikerentals.security.JwtService; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.*; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.web.bind.annotation.*; import java.util.Map;
@RestController @RequestMapping("/api/auth") public class AuthController {
 private final UserRepository users; private final PasswordEncoder encoder; private final JwtService jwt;
 public AuthController(UserRepository u,PasswordEncoder e,JwtService j){users=u;encoder=e;jwt=j;}
 record Register(@NotBlank String name,@Email @NotBlank String email,@NotBlank @Size(min=8) String password,String phone,String address){}
 record Login(@Email @NotBlank String email,@NotBlank String password){}
 @PostMapping("/register") public ResponseEntity<?> register(@Valid @RequestBody Register r){String email=r.email().toLowerCase();if(users.existsByEmail(email))return ResponseEntity.status(409).body(Map.of("message","Email is already registered"));User u=new User();u.name=r.name();u.email=email;u.password=encoder.encode(r.password());u.phone=r.phone();u.address=r.address();u.role="CUSTOMER";users.save(u);return ResponseEntity.status(201).body(Map.of("message","Registered successfully"));}
 @PostMapping("/login") public ResponseEntity<?> login(@Valid @RequestBody Login r){var u=users.findByEmail(r.email().toLowerCase()).orElse(null);if(u==null||!u.active||!encoder.matches(r.password(),u.password))return ResponseEntity.status(401).body(Map.of("message","Invalid email or password"));return ResponseEntity.ok(Map.of("token",jwt.create(u.email,u.role),"user",Map.of("id",u.id,"name",u.name,"email",u.email,"role",u.role)));}
}
