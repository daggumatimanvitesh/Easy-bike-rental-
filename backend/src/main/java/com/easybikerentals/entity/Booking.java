package com.easybikerentals.entity;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.LocalDateTime;
@Entity @Table(name="bookings",indexes=@Index(columnList="bike_id,pickup_at,return_at,status")) public class Booking {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(unique=true,nullable=false) public String bookingCode;
 @ManyToOne(optional=false) public User customer; @ManyToOne(optional=false) public Bike bike;
 @Column(nullable=false) public LocalDateTime pickupAt; @Column(nullable=false) public LocalDateTime returnAt;
 public String pickupLocation; public String returnLocation; public String licenseNumber;
 @Column(nullable=false) public String status="PENDING"; @Column(nullable=false,precision=10,scale=2) public BigDecimal totalAmount=BigDecimal.ZERO; public LocalDateTime createdAt=LocalDateTime.now(); public Booking(){}
}
