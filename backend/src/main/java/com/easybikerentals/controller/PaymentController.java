package com.easybikerentals.controller;

import com.easybikerentals.entity.Booking;
import com.easybikerentals.entity.Payment;
import com.easybikerentals.repository.BookingRepository;
import com.easybikerentals.repository.PaymentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
 private final PaymentRepository payments;
 private final BookingRepository bookings;

 public PaymentController(PaymentRepository payments,BookingRepository bookings){this.payments=payments;this.bookings=bookings;}

 record TestPayment(@NotNull Long bookingId,@NotBlank String method){}

 @PostMapping
 @Transactional
 public ResponseEntity<Map<String,Object>> simulate(@Valid @RequestBody TestPayment request){
  String method=request.method().trim().toUpperCase(Locale.ROOT);
  if(!List.of("CASH","UPI","CARD").contains(method))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment method must be CASH, UPI or CARD");

  Booking booking=bookings.lockBooking(request.bookingId());
  if(booking==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found");
  String email=SecurityContextHolder.getContext().getAuthentication().getName();
  if(!booking.customer.email.equals(email))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This booking belongs to another customer");
  if(!List.of("PENDING","CONFIRMED").contains(booking.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"This booking is not eligible for payment");

  Payment payment=payments.findFirstByBooking_IdOrderByPaidAtDesc(booking.id).orElse(null);
  boolean created=payment==null;
  if(payment==null){
   payment=new Payment();
   payment.booking=booking;
   payment.amount=booking.totalAmount;
   payment.method=method;
   payment.status="TEST_PAID";
   payment.transactionReference="TEST-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
   payment=payments.save(payment);
  }

  if("PENDING".equals(booking.status))booking.status="CONFIRMED";
  bookings.save(booking);
  var body=Map.<String,Object>of("message","Test payment recorded","paymentStatus",payment.status,"bookingStatus",booking.status,"transactionReference",payment.transactionReference,"amount",payment.amount,"method",payment.method);
  return ResponseEntity.status(created?HttpStatus.CREATED:HttpStatus.OK).body(body);
 }

 @GetMapping
 public List<Payment> all(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return payments.findAllByOrderByPaidAtDesc();}
}
