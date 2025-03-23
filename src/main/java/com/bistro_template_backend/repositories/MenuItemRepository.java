package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByComplexItemTrue();

    List<MenuItem> findByCategoryId(Long categoryId);

    List<MenuItem> findByIsFeaturedTrue();
}
