package com.easybikerentals.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Service
public class RazorpayService {
 private static final String API_URL="https://api.razorpay.com/v1";
 private final String keyId;
 private final String keySecret;
 private final String webhookSecret;
 private final RestClient client;

 public RazorpayService(@Value("${app.razorpay.key-id:}") String keyId,
                        @Value("${app.razorpay.key-secret:}") String keySecret,
                        @Value("${app.razorpay.webhook-secret:}") String webhookSecret){
  this.keyId=keyId.trim();this.keySecret=keySecret.trim();this.webhookSecret=webhookSecret.trim();
  this.client=RestClient.builder().baseUrl(API_URL).defaultHeaders(headers->headers.setBasicAuth(this.keyId,this.keySecret)).build();
 }

 public boolean isConfigured(){return !keyId.isBlank()&&!keySecret.isBlank();}
 public String keyId(){requireConfigured();return keyId;}

 public Map<String,Object> createOrder(long amount,String receipt,Map<String,String> notes){
  requireConfigured();
  return client.post().uri("/orders").body(Map.of("amount",amount,"currency","INR","receipt",receipt,"notes",notes)).retrieve().body(new ParameterizedTypeReference<>(){});
 }

 public Map<String,Object> fetchPayment(String paymentId){
  requireConfigured();
  return client.get().uri("/payments/{id}",paymentId).retrieve().body(new ParameterizedTypeReference<>(){});
 }

 public boolean verifyCheckoutSignature(String orderId,String paymentId,String received){
  requireConfigured();
  return verifyHmac(orderId+"|"+paymentId,keySecret,received);
 }

 public boolean verifyWebhookSignature(String rawBody,String received){
  if(webhookSecret.isBlank())return false;
  return verifyHmac(rawBody,webhookSecret,received);
 }

 private void requireConfigured(){if(!isConfigured())throw new IllegalStateException("Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET on the backend.");}

 private static boolean verifyHmac(String message,String secret,String received){
  if(received==null||received.isBlank())return false;
  try{
   Mac mac=Mac.getInstance("HmacSHA256");
   mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
   byte[] expected=HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII);
   return MessageDigest.isEqual(expected,received.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
  }catch(Exception e){throw new IllegalStateException("Could not verify Razorpay signature",e);}
 }
}
