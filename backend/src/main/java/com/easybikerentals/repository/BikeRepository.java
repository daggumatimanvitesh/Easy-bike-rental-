package com.easybikerentals.repository;
import com.easybikerentals.entity.Bike; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface BikeRepository extends JpaRepository<Bike,Long> { List<Bike> findByActiveTrue(); }
