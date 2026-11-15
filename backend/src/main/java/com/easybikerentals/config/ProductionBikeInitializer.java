package com.easybikerentals.config;

import com.easybikerentals.entity.Bike;
import com.easybikerentals.repository.BikeRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.List;

@Configuration
@Profile("production")
public class ProductionBikeInitializer {
    @Bean
    ApplicationRunner initializeBikeFleet(BikeRepository bikes) {
        return args -> {
            if (bikes.count() != 0) return;

            bikes.saveAll(List.of(
                    bike("Honda Activa 6G", "Honda", "Activa 6G", "Scooter", "Petrol", 110,
                            "80", "650", "1500", "Comfortable city scooter with excellent mileage.",
                            "https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&w=900&q=80"),
                    bike("Royal Enfield Classic 350", "Royal Enfield", "Classic 350", "Cruiser", "Petrol", 349,
                            "180", "1400", "4000", "A timeless cruiser for weekend rides.",
                            "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?auto=format&fit=crop&w=900&q=80"),
                    bike("Yamaha MT-15 V2", "Yamaha", "MT-15 V2", "Sports", "Petrol", 155,
                            "150", "1200", "3500", "Light, agile performance for city and open roads.",
                            "https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&w=900&q=80"),
                    bike("Ather 450X", "Ather", "450X", "Electric", "Electric", 0,
                            "120", "950", "2500", "Quick and quiet electric city ride.",
                            "https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&w=900&q=80")
            ));
        };
    }

    private Bike bike(String name, String brand, String model, String category, String fuelType, int engineCc,
                      String hourly, String daily, String deposit, String description, String imageUrl) {
        var bike = new Bike();
        bike.name = name;
        bike.brand = brand;
        bike.model = model;
        bike.category = category;
        bike.fuelType = fuelType;
        bike.engineCc = engineCc;
        bike.pricePerHour = new BigDecimal(hourly);
        bike.pricePerDay = new BigDecimal(daily);
        bike.securityDeposit = new BigDecimal(deposit);
        bike.description = description;
        bike.imageUrl = imageUrl;
        bike.active = true;
        return bike;
    }
}
