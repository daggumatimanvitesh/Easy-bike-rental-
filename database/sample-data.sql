USE easy_bike_rentals_db;
INSERT INTO roles(name) VALUES('ADMIN'),('CUSTOMER') ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT INTO bike_categories(name) VALUES('Scooter'),('Sports'),('Standard'),('Cruiser'),('Electric') ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT INTO bikes(name,brand,model,category,fuel_type,engine_cc,price_per_hour,price_per_day,security_deposit,description,image_url) VALUES
('Honda Activa 6G','Honda','Activa 6G','Scooter','Petrol',110,80,650,1500,'Comfortable city scooter with excellent mileage.','https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&w=900&q=80'),
('Royal Enfield Classic 350','Royal Enfield','Classic 350','Cruiser','Petrol',349,180,1400,4000,'A timeless cruiser for weekend rides.','https://images.unsplash.com/photo-1558981403-c5f9899a28bc?auto=format&fit=crop&w=900&q=80'),
('Yamaha MT-15 V2','Yamaha','MT-15 V2','Sports','Petrol',155,150,1200,3500,'Light, agile performance for city and open roads.','https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&w=900&q=80'),
('Ather 450X','Ather','450X','Electric','Electric',0,120,950,2500,'Quick and quiet electric city ride.','https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&w=900&q=80');
INSERT INTO rental_locations(name,address,city) VALUES('Bengaluru Central','MG Road, Bengaluru','Bengaluru'),('Indiranagar Hub','100 Feet Road, Indiranagar','Bengaluru');
-- No demo users or passwords are seeded. Register, then optionally promote an account locally: UPDATE users SET role='ADMIN' WHERE email='your@email';
