package com.easybikerentals.entity;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="contact_messages") public class ContactMessage { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; public String name; public String email; public String phone; public String subject; @Column(length=4000) public String message; public Instant createdAt=Instant.now(); public ContactMessage(){} }
