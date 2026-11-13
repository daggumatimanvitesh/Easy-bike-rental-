package com.easybikerentals.repository;
import com.easybikerentals.entity.ContactMessage; import org.springframework.data.jpa.repository.JpaRepository;
public interface ContactRepository extends JpaRepository<ContactMessage,Long> {}
