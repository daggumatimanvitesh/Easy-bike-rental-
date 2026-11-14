package com.easybikerentals.entity;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.LocalDateTime;
@Entity @Table(name="payments") public class Payment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false) public Booking booking;
 @Column(nullable=false,precision=10,scale=2) public BigDecimal amount;
 @Column(nullable=false) public String method;
 @Column(nullable=false) public String status="CREATED";
 @Column(unique=true) public String gatewayOrderId;
 @Column(unique=true) public String gatewayPaymentId;
 @Column(nullable=false) public String currency="INR";
 public String transactionReference;
 public LocalDateTime paidAt=LocalDateTime.now();
 public Payment(){}
}
