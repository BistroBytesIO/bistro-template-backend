package com.bistro_template_backend.controllers;

import com.bistro_template_backend.models.MenuItem;
import com.bistro_template_backend.services.MenuItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    @Autowired
    private MenuItemService menuItemService;

    @GetMapping
    public ResponseEntity<List<MenuItem>> getMenuItems() {
        return ResponseEntity.ok(menuItemService.getAllMenuItems());
    }

    @PostMapping
    public MenuItem createMenuItem(@RequestBody MenuItem menuItem) {
        return menuItemService.createMenuItem(menuItem);
    }

    @GetMapping("/featured")
    public ResponseEntity<List<MenuItem>> getFeaturedItems() {
        List<MenuItem> featuredItems = menuItemService.getFeaturedItems();
        return ResponseEntity.ok(featuredItems);
    }

    @GetMapping("/reward-items")
    public ResponseEntity<List<MenuItem>> getRewardItems() {
        List<MenuItem> rewardItems = menuItemService.getRewardItems();
        return ResponseEntity.ok(rewardItems);
    }
}
