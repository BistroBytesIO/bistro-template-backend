package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.Customization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomizationRepository extends JpaRepository<Customization, Long> {
    List<Customization> findByMenuItemId(Long menuItemId);
    
    // Voice AI integration methods
    Optional<Customization> findByName(String name);
    List<Customization> findByNameContainingIgnoreCase(String name);
}