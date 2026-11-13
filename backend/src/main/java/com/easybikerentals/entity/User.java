package com.easybikerentals.entity;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="users", uniqueConstraints=@UniqueConstraint(columnNames="email"))
public class User {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,length=120) public String name;
 @Column(nullable=false,length=180,unique=true) public String email;
 @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) @Column(nullable=false) public String password;
 public String phone; @Column(length=500) public String address;
 @Column(nullable=false) public String role="CUSTOMER";
 @Column(nullable=false) public boolean active=true; public Instant createdAt=Instant.now();
 public User(){}
}
