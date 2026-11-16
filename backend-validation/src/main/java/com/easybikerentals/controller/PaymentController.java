package com.easybikerentals.controller;

import com.easybikerentals.entity.Booking;
import com.easybikerentals.entity.Payment;
import com.easybikerentals.entity.RazorpayWebhookEvent;
import com.easybikerentals.repository.BookingRepository;
import com.easybikerentals.repository.PaymentRepository;
import com.easybikerentals.repository.RazorpayWebhookEventRepository;
import com.easybikerentals.service.RazorpayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
 private final PaymentRepository payments;
 private final BookingRepository bookings;
 private final RazorpayService razorpay;
 private final RazorpayWebhookEventRepository webhookEvents;
 private final ObjectMapper objectMapper;
 @Value("${app.payments.demo-enabled:false}") private boolean demoPaymentsEnabled;

 public PaymentController(PaymentRepository payments,BookingRepository bookings,RazorpayService razorpay,RazorpayWebhookEventRepository webhookEvents,ObjectMapper objectMapper){this.payments=payments;this.bookings=bookings;this.razorpay=razorpay;this.webhookEvents=webhookEvents;this.objectMapper=objectMapper;}

 record OrderRequest(@NotNull Long bookingId){}
 record Verification(@NotBlank String razorpay_order_id,@NotBlank String razorpay_payment_id,@NotBlank String razorpay_signature){}

 @PostMapping("/orders")
 @Transactional
 public Map<String,Object> createOrder(@Valid @RequestBody OrderRequest request){
  requireRazorpay();
  Booking booking=ownedBooking(request.bookingId());
  if(!List.of("PENDING","CONFIRMED").contains(booking.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"This booking is not eligible for payment");
  Payment payment=payments.findFirstByBooking_IdOrderByPaidAtDesc(booking.id).orElse(null);
  if(payment!=null&&"CAPTURED".equals(payment.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"This booking has already been paid");
  if(payment!=null&&"TEST_PAID".equals(payment.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"This booking has already been completed with a test payment");
  if(payment!=null&&payment.gatewayOrderId!=null)return orderResponse(payment);

  if(payment==null){payment=new Payment();payment.booking=booking;payment.amount=booking.totalAmount;payment.currency="INR";payment.method="RAZORPAY";payment.status="CREATED";payment=payments.saveAndFlush(payment);}
  long amountMinor;
  try{amountMinor=payment.amount.setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();}
  catch(ArithmeticException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Booking amount cannot be charged as INR",ex);}
  String receipt="EBR-"+booking.id+"-"+payment.id+"-"+System.currentTimeMillis();
  try{
   Map<String,Object> order=razorpay.createOrder(amountMinor,receipt,Map.of("booking_code",booking.bookingCode,"booking_id",booking.id.toString()));
   payment.gatewayOrderId=String.valueOf(order.get("id"));
   if(payment.gatewayOrderId.isBlank()||"null".equals(payment.gatewayOrderId))throw new IllegalStateException("Razorpay did not return an order id");
   payments.save(payment);
   return orderResponse(payment);
  }catch(RestClientException|IllegalStateException ex){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Razorpay could not create a payment order. Check the server credentials and Razorpay account status.",ex);}
 }

 @PostMapping("/demo")
 @Transactional
 public Map<String,Object> completeDemoPayment(@Valid @RequestBody OrderRequest request){
  if(!demoPaymentsEnabled)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Demo payments are disabled");
  Booking booking=ownedBooking(request.bookingId());
  Payment payment=payments.findFirstByBooking_IdOrderByPaidAtDesc(booking.id).orElse(null);
  if(payment!=null&&"TEST_PAID".equals(payment.status)){
   if("CONFIRMED".equals(booking.status))return demoConfirmation(payment,booking);
   if("PENDING".equals(booking.status)){booking.status="CONFIRMED";bookings.save(booking);return demoConfirmation(payment,booking);}
   throw new ResponseStatusException(HttpStatus.CONFLICT,"This test payment is already recorded, but the booking is no longer active");
  }
  if("CONFIRMED".equals(booking.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"This booking is already confirmed");
  if(!"PENDING".equals(booking.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"This booking is not eligible for a test payment");
  if(payment!=null&&("CAPTURED".equals(payment.status)||payment.gatewayOrderId!=null))throw new ResponseStatusException(HttpStatus.CONFLICT,"A Razorpay payment has already been started for this booking. Finish or resolve that payment first");
  if(payment==null){payment=new Payment();payment.booking=booking;}
  payment.amount=booking.totalAmount;payment.currency="INR";payment.method="DEMO";payment.status="TEST_PAID";
  payment.transactionReference="TEST-"+java.util.UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  payment.paidAt=java.time.LocalDateTime.now();payment=payments.save(payment);
  booking.status="CONFIRMED";bookings.save(booking);
  return demoConfirmation(payment,booking);
 }

 @PostMapping("/verify")
 @Transactional
 public Map<String,Object> verify(@Valid @RequestBody Verification request){
  requireRazorpay();
  Payment payment=payments.findByGatewayOrderId(request.razorpay_order_id()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment order not found"));
  Booking booking=ownedBooking(payment.booking.id);
  if(!razorpay.verifyCheckoutSignature(payment.gatewayOrderId,request.razorpay_payment_id(),request.razorpay_signature()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Razorpay payment signature is invalid");
  Map<String,Object> gatewayPayment;
  try{gatewayPayment=razorpay.fetchPayment(request.razorpay_payment_id());}
  catch(RestClientException ex){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Could not verify the payment with Razorpay. Refresh your bookings before trying again.",ex);}
  if(!payment.gatewayOrderId.equals(gatewayPayment.get("order_id"))||number(gatewayPayment.get("amount"))!=toMinorUnits(payment)||!payment.currency.equals(gatewayPayment.get("currency")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Razorpay payment does not match this booking");
  String gatewayStatus=String.valueOf(gatewayPayment.get("status"));
  if(!"captured".equalsIgnoreCase(gatewayStatus))throw new ResponseStatusException(HttpStatus.CONFLICT,"Razorpay has not captured this payment yet. Do not retry the payment; check your Razorpay Dashboard or enable automatic capture.");
  if(!"CAPTURED".equals(payment.status)){
   payment.gatewayPaymentId=request.razorpay_payment_id();
   payment.transactionReference=request.razorpay_payment_id();
   payment.method=String.valueOf(gatewayPayment.getOrDefault("method","RAZORPAY")).toUpperCase(Locale.ROOT);
   payment.status="CAPTURED";
   payments.save(payment);
  }
  if("PENDING".equals(booking.status))booking.status="CONFIRMED";
  bookings.save(booking);
  return confirmation(payment,booking);
 }

 @GetMapping
 public List<Payment> all(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return payments.findAllByOrderByPaidAtDesc();}

 @PostMapping("/webhook")
 @Transactional
 public ResponseEntity<Void> webhook(@RequestBody String rawBody,@RequestHeader("X-Razorpay-Signature") String signature,@RequestHeader("x-razorpay-event-id") String eventId){
  if(!razorpay.verifyWebhookSignature(rawBody,signature))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Razorpay webhook signature");
  if(webhookEvents.existsByEventId(eventId))return ResponseEntity.ok().build();
  JsonNode payload;
  try{payload=objectMapper.readTree(rawBody);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid webhook payload",ex);}
  String eventType=payload.path("event").asText("");
  if("payment.captured".equals(eventType))processCapturedWebhook(payload.path("payload").path("payment").path("entity"));
  var event=new RazorpayWebhookEvent();event.eventId=eventId;event.eventType=eventType.isBlank()?"unknown":eventType;webhookEvents.save(event);
  return ResponseEntity.ok().build();
 }

 private void processCapturedWebhook(JsonNode gatewayPayment){
  String orderId=gatewayPayment.path("order_id").asText("");
  String paymentId=gatewayPayment.path("id").asText("");
  if(orderId.isBlank()||paymentId.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Captured payment webhook is missing its payment details");
  Payment payment=payments.findByGatewayOrderId(orderId).orElse(null);
  if(payment==null)return;
  Booking booking=bookings.lockBooking(payment.booking.id);
  if(booking==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found for captured payment");
  if(gatewayPayment.path("amount").asLong(-1)!=toMinorUnits(payment)||!payment.currency.equals(gatewayPayment.path("currency").asText("")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Captured Razorpay payment does not match the booking amount");
  payment.gatewayPaymentId=paymentId;payment.transactionReference=paymentId;payment.method=gatewayPayment.path("method").asText("RAZORPAY").toUpperCase(Locale.ROOT);payment.status="CAPTURED";payments.save(payment);
  if("PENDING".equals(booking.status))booking.status="CONFIRMED";
  bookings.save(booking);
 }

 private Booking ownedBooking(Long id){
  Booking booking=bookings.lockBooking(id);
  if(booking==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found");
  String email=SecurityContextHolder.getContext().getAuthentication().getName();
  if(!booking.customer.email.equals(email))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This booking belongs to another customer");
  return booking;
 }

 private Map<String,Object> orderResponse(Payment payment){return Map.of("keyId",razorpay.keyId(),"orderId",payment.gatewayOrderId,"amount",toMinorUnits(payment),"currency",payment.currency,"bookingId",payment.booking.id);}
 private Map<String,Object> demoConfirmation(Payment payment,Booking booking){return Map.of("message","Test payment completed. No money was charged.","paymentStatus",payment.status,"bookingStatus",booking.status,"transactionReference",payment.transactionReference,"amount",payment.amount,"method",payment.method);}
 private Map<String,Object> confirmation(Payment payment,Booking booking){return Map.of("message","Razorpay payment verified","paymentStatus",payment.status,"bookingStatus",booking.status,"transactionReference",payment.transactionReference,"amount",payment.amount,"method",payment.method);}
 private long toMinorUnits(Payment payment){return payment.amount.setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();}
 private long number(Object value){if(value instanceof Number n)return n.longValue();throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Razorpay returned an invalid payment amount");}
 private void requireRazorpay(){if(!razorpay.isConfigured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET on the backend.");}
}
