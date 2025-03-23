package com.bistro_template_backend.services;

import com.bistro_template_backend.models.MenuItem;
import com.bistro_template_backend.repositories.MenuItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;

    // Constructor injection (recommended)
    public MenuItemService(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    public List<MenuItem> getMenuItemsByCategory(Long categoryId) {
        return menuItemRepository.findByCategoryId(categoryId);
    }

    /**
     * Retrieves all MenuItem records from the database.
     *
     * @return a list of all MenuItem objects
     */
    public List<MenuItem> getAllMenuItems() {
        return menuItemRepository.findAll();
    }

    /**
     * Creates (saves) a new MenuItem in the database.
     *
     * @param menuItem the MenuItem object to be saved
     * @return the saved MenuItem, including any generated ID
     */
    public MenuItem createMenuItem(MenuItem menuItem) {
        return menuItemRepository.save(menuItem);
    }

    public List<MenuItem> getFeaturedItems() {
        return menuItemRepository.findByIsFeaturedTrue();
    }
}