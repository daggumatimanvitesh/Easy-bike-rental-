package com.easybikerentals.repository;
import com.easybikerentals.entity.Booking; import com.easybikerentals.entity.Bike; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType; import java.time.LocalDateTime; import java.util.List;
public interface BookingRepository extends JpaRepository<Booking,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from Bike b where b.id=:id") Bike lockBike(@Param("id") Long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from Booking b where b.id=:id") Booking lockBooking(@Param("id") Long id);
 @Query("select count(b)>0 from Booking b where b.bike.id=:bike and b.status in ('PENDING','CONFIRMED','PICKED_UP') and b.pickupAt < :end and b.returnAt > :start") boolean overlaps(@Param("bike")Long bike,@Param("start")LocalDateTime start,@Param("end")LocalDateTime end);
 List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId); List<Booking> findAllByOrderByCreatedAtDesc();
}
