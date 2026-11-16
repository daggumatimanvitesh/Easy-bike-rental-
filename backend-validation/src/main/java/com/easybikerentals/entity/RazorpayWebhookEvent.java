package com.easybikerentals.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="razorpay_webhook_events")
public class RazorpayWebhookEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,unique=true,length=100) public String eventId;
 @Column(nullable=false,length=100) public String eventType;
 @Column(nullable=false) public Instant receivedAt=Instant.now();
 public RazorpayWebhookEvent(){}
}
