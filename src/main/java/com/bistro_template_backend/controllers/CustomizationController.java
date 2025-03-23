package com.bistro_template_backend.controllers;

import com.bistro_template_backend.models.Customization;
import com.bistro_template_backend.models.MenuItem;
import com.bistro_template_backend.services.CustomizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menu")
public class CustomizationController {

    @Autowired
    private CustomizationService customizationService;

    // Get menu item with its customizations
    @GetMapping("/{id}/customizations")
    public ResponseEntity<?> getCustomizations(@PathVariable Long id) {
        MenuItem menuItem = customizationService.getMenuItem(id);
        List<Customization> customizations = customizationService.getCustomizationsForItem(id);

        Map<String, Object> response = new HashMap<>();
        response.put("menuItem", menuItem);
        response.put("customizations", customizations);

        return ResponseEntity.ok(response);
    }
}
