package com.easybikerentals.entity;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="bikes") public class Bike {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false) public String name; public String brand; public String model; public String category; public String fuelType; public Integer engineCc;
 @Column(nullable=false,precision=10,scale=2) public BigDecimal pricePerHour;
 @Column(nullable=false,precision=10,scale=2) public BigDecimal pricePerDay;
 public BigDecimal securityDeposit=BigDecimal.ZERO; @Column(length=1000) public String description; @Column(length=1000) public String imageUrl;
 @Column(nullable=false) public boolean active=true; public Bike(){}
}
