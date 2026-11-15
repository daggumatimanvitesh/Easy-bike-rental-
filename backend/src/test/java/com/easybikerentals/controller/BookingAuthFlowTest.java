package com.easybikerentals.controller;

import com.easybikerentals.config.SecurityConfig;
import com.easybikerentals.entity.Bike;
import com.easybikerentals.entity.Booking;
import com.easybikerentals.entity.Payment;
import com.easybikerentals.entity.User;
import com.easybikerentals.repository.BikeRepository;
import com.easybikerentals.repository.BookingRepository;
import com.easybikerentals.repository.PaymentRepository;
import com.easybikerentals.repository.RazorpayWebhookEventRepository;
import com.easybikerentals.repository.UserRepository;
import com.easybikerentals.security.JwtFilter;
import com.easybikerentals.security.JwtService;
import com.easybikerentals.service.RazorpayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BookingController.class, PaymentController.class}, properties = {
        "app.cors.origin=http://127.0.0.1:5174",
        "app.payments.demo-enabled=true"
})
@Import({SecurityConfig.class, JwtFilter.class})
class BookingAuthFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtService jwtService;
    @MockBean private UserRepository users;
    @MockBean private BookingRepository bookings;
    @MockBean private BikeRepository bikes;
    @MockBean private PaymentRepository payments;
    @MockBean private RazorpayService razorpay;
    @MockBean private RazorpayWebhookEventRepository webhookEvents;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.id = 42L;
        customer.name = "Test Rider";
        customer.email = "rider@example.test";
        customer.role = "CUSTOMER";
        customer.active = true;
        when(jwtService.email("test-token")).thenReturn(customer.email);
        when(users.findByEmail(customer.email)).thenReturn(Optional.of(customer));
    }

    @Test
    void bookingRequiresBearerToken() throws Exception {
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBookingRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_BEARER_TOKEN"));

        verify(bookings, never()).save(any(Booking.class));
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        when(jwtService.email("bad-token")).thenThrow(new IllegalArgumentException("invalid token"));

        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer bad-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBookingRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REJECTED"));

        verify(bookings, never()).save(any(Booking.class));
    }

    @Test
    void authenticatedCustomerCreatesPendingBookingOwnedByVerifiedUser() throws Exception {
        Bike bike = new Bike();
        bike.id = 7L;
        bike.name = "Test Bike";
        bike.pricePerDay = new BigDecimal("1200.00");
        bike.securityDeposit = new BigDecimal("3500.00");
        when(bookings.lockBike(7L)).thenReturn(bike);
        when(bookings.overlaps(eq(7L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(false);
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.id = 11L;
            return booking;
        });

        mvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBookingRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.customer.email").value(customer.email))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(4700.0));
    }

    @Test
    void testPaymentConfirmsOwnedBookingAndCanBeRetriedIdempotently() throws Exception {
        Booking booking = pendingBooking();
        when(bookings.lockBooking(11L)).thenReturn(booking);
        when(payments.findFirstByBooking_IdOrderByPaidAtDesc(11L)).thenReturn(Optional.empty());
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.id = 25L;
            return payment;
        });

        mvc.perform(post("/api/payments/demo")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("TEST_PAID"))
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));

        Payment paid = new Payment();
        paid.id = 25L;
        paid.booking = booking;
        paid.amount = booking.totalAmount;
        paid.currency = "INR";
        paid.method = "DEMO";
        paid.status = "TEST_PAID";
        paid.transactionReference = "TEST-REF";
        booking.status = "CONFIRMED";
        when(payments.findFirstByBooking_IdOrderByPaidAtDesc(11L)).thenReturn(Optional.of(paid));

        mvc.perform(post("/api/payments/demo")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value("TEST-REF"))
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));

        verify(payments).save(any(Payment.class));
    }

    @Test
    void customerCannotPayAnotherCustomersBooking() throws Exception {
        Booking booking = pendingBooking();
        User other = new User();
        other.email = "other@example.test";
        booking.customer = other;
        when(bookings.lockBooking(11L)).thenReturn(booking);

        mvc.perform(post("/api/payments/demo")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":11}"))
                .andExpect(status().isForbidden());

        verify(payments, never()).save(any(Payment.class));
    }

    private Booking pendingBooking() {
        Booking booking = new Booking();
        booking.id = 11L;
        booking.customer = customer;
        booking.status = "PENDING";
        booking.totalAmount = new BigDecimal("4700.00");
        return booking;
    }

    private String validBookingRequest() throws Exception {
        LocalDateTime pickup = LocalDate.now().plusDays(2).atTime(10, 0);
        LocalDateTime returned = pickup.plusDays(1);
        return objectMapper.writeValueAsString(new BookingRequest(
                7L, pickup, returned, "Bengaluru Central", "Bengaluru Central", "TEST12345", 999L));
    }

    private record BookingRequest(Long bikeId, LocalDateTime pickupAt, LocalDateTime returnAt,
                                  String pickupLocation, String returnLocation, String licenseNumber,
                                  Long customerId) {}
}
