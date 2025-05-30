package com.example.gadgetariumb8.db.repository;

import com.example.gadgetariumb8.db.model.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {

}
