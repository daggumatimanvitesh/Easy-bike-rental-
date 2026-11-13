package com.easybikerentals.controller;
import com.easybikerentals.entity.Bike; import com.easybikerentals.repository.BikeRepository; import org.springframework.http.HttpStatus; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import java.util.List;
@RestController @RequestMapping("/api/bikes") public class BikeController {
 private final BikeRepository repo; public BikeController(BikeRepository r){repo=r;}
 @GetMapping public List<Bike> all(){return repo.findByActiveTrue();}
 @GetMapping("/{id}") public Bike get(@PathVariable Long id){return repo.findById(id).filter(b->b.active).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Bike not found"));}
 @PostMapping @PreAuthorize("hasRole('ADMIN')") public Bike add(@RequestBody Bike b){b.id=null;return repo.save(b);}
 @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public Bike update(@PathVariable Long id,@RequestBody Bike b){Bike old=repo.findById(id).orElseThrow();b.id=old.id;return repo.save(b);}
 @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public void delete(@PathVariable Long id){Bike b=repo.findById(id).orElseThrow();b.active=false;repo.save(b);}
}
