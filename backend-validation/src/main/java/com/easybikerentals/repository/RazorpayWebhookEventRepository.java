package com.easybikerentals.repository;

import com.easybikerentals.entity.RazorpayWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RazorpayWebhookEventRepository extends JpaRepository<RazorpayWebhookEvent,Long> {
 boolean existsByEventId(String eventId);
}
