package com.bistro_template_backend.services;

import com.bistro_template_backend.models.MenuItem;
import com.bistro_template_backend.models.Customization;
import com.bistro_template_backend.repositories.MenuItemRepository;
import com.bistro_template_backend.repositories.CustomizationRepository;
import com.bistro_template_backend.services.VoiceSessionManager.VoiceOrder;
import com.bistro_template_backend.services.VoiceSessionManager.VoiceOrderItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationContextService {

    private final MenuItemRepository menuItemRepository;
    private final CustomizationRepository customizationRepository;
    @Lazy private final CategoryService categoryService;
    @Lazy private final VoiceSessionManager voiceSessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Build comprehensive menu context for AI conversation
     */
    @Cacheable(value = "menu-cache", key = "'full-menu-context'")
    public String buildMenuContext() {
        try {
            List<MenuItem> allMenuItems = menuItemRepository.findAll();
            
            // Group items by category
            Map<String, List<MenuItem>> itemsByCategory = allMenuItems.stream()
                    .filter(item -> item.getIsAvailable() != null && item.getIsAvailable())
                    .collect(Collectors.groupingBy(
                            item -> item.getCategory() != null ? item.getCategory() : "Other"
                    ));

            StringBuilder context = new StringBuilder();
            context.append("RESTAURANT MENU:\n\n");

            // Build context for each category
            for (Map.Entry<String, List<MenuItem>> entry : itemsByCategory.entrySet()) {
                String category = entry.getKey();
                List<MenuItem> items = entry.getValue();

                context.append(category.toUpperCase()).append(":\n");
                
                for (MenuItem item : items) {
                    context.append("- ").append(item.getName());
                    
                    if (item.getPrice() != null) {
                        context.append(" ($").append(item.getPrice()).append(")");
                    }
                    
                    if (item.getDescription() != null && !item.getDescription().trim().isEmpty()) {
                        context.append(" - ").append(item.getDescription());
                    }
                    
                    // Add availability info
                    if (item.getStockQuantity() != null) {
                        if (item.getStockQuantity() == 0) {
                            context.append(" [OUT OF STOCK]");
                        } else if (item.getStockQuantity() < 5) {
                            context.append(" [LIMITED STOCK]");
                        }
                    }
                    
                    // Add customization context
                    context.append(buildCustomizationContext(item));
                    
                    context.append("\n");
                }
                context.append("\n");
            }

            // Add ordering guidelines
            context.append("ORDERING GUIDELINES:\n");
            context.append("- Always confirm quantities and customizations\n");
            context.append("- Ask about dietary restrictions or allergies\n");
            context.append("- Suggest popular items when appropriate\n");
            context.append("- Confirm the complete order before processing payment\n");
            context.append("- Be helpful with menu questions and recommendations\n\n");

            String fullContext = context.toString();
            log.debug("Built menu context with {} items across {} categories", 
                    allMenuItems.size(), itemsByCategory.size());
            
            return fullContext;

        } catch (Exception e) {
            log.error("Error building menu context", e);
            return "Menu information is temporarily unavailable. Please try again.";
        }
    }

    /**
     * Build focused menu context based on user intent
     */
    public String buildFocusedMenuContext(String userMessage, String sessionId) {
        try {
            // Analyze user message for category intent
            Set<String> mentionedCategories = extractMentionedCategories(userMessage);
            Set<String> mentionedItems = extractMentionedItems(userMessage);

            if (mentionedCategories.isEmpty() && mentionedItems.isEmpty()) {
                // Return a condensed version of the full menu
                return buildCondensedMenuContext();
            }

            StringBuilder context = new StringBuilder();
            context.append("RELEVANT MENU ITEMS:\n\n");

            // Add items from mentioned categories
            for (String category : mentionedCategories) {
                List<MenuItem> categoryItems = menuItemRepository.findByCategoryAndIsAvailable(category, true);
                if (!categoryItems.isEmpty()) {
                    context.append(category.toUpperCase()).append(":\n");
                    for (MenuItem item : categoryItems) {
                        context.append("- ").append(item.getName())
                               .append(" ($").append(item.getPrice()).append(")");
                        
                        if (item.getDescription() != null) {
                            context.append(" - ").append(item.getDescription());
                        }
                        
                        // Add customization context
                        context.append(buildCustomizationContext(item));
                        
                        context.append("\n");
                    }
                    context.append("\n");
                }
            }

            // Add specifically mentioned items with details
            for (String itemName : mentionedItems) {
                List<MenuItem> matchingItems = findMenuItemsByName(itemName);
                if (!matchingItems.isEmpty()) {
                    context.append("ITEM DETAILS:\n");
                    for (MenuItem item : matchingItems) {
                        context.append("- ").append(item.getName())
                               .append(" ($").append(item.getPrice()).append(")");
                        
                        if (item.getDescription() != null) {
                            context.append("\n  ").append(item.getDescription());
                        }
                        
                        if (item.getStockQuantity() != null && item.getStockQuantity() < 5) {
                            context.append("\n  [Limited Stock: ").append(item.getStockQuantity()).append(" remaining]");
                        }
                        
                        // Add customization context
                        context.append(buildCustomizationContext(item));
                        
                        context.append("\n");
                    }
                    context.append("\n");
                }
            }

            return context.toString();

        } catch (Exception e) {
            log.error("Error building focused menu context", e);
            return buildCondensedMenuContext();
        }
    }

    /**
     * Build condensed menu context with highlights
     */
    private String buildCondensedMenuContext() {
        try {
            List<MenuItem> featuredItems = menuItemRepository.findByIsFeaturedAndIsAvailable(true, true);
            List<MenuItem> allItems = menuItemRepository.findByIsAvailable(true);
            
            StringBuilder context = new StringBuilder();
            context.append("MENU HIGHLIGHTS:\n\n");
            
            if (!featuredItems.isEmpty()) {
                context.append("FEATURED ITEMS:\n");
                for (MenuItem item : featuredItems) {
                    context.append("- ").append(item.getName())
                           .append(" ($").append(item.getPrice()).append(")");
                    if (item.getDescription() != null) {
                        context.append(" - ").append(item.getDescription());
                    }
                    context.append("\n");
                }
                context.append("\n");
            }
            
            // Group by category and show a few items per category
            Map<String, List<MenuItem>> itemsByCategory = allItems.stream()
                    .collect(Collectors.groupingBy(
                            item -> item.getCategory() != null ? item.getCategory() : "Other"
                    ));
            
            context.append("CATEGORIES AVAILABLE:\n");
            for (Map.Entry<String, List<MenuItem>> entry : itemsByCategory.entrySet()) {
                context.append("- ").append(entry.getKey())
                       .append(" (").append(entry.getValue().size()).append(" items)\n");
            }
            
            return context.toString();
            
        } catch (Exception e) {
            log.error("Error building condensed menu context", e);
            return "Full menu available. Please ask about specific items or categories.";
        }
    }

    /**
     * Process user intent and update order context
     */
    public OrderUpdateResult processOrderIntent(String userMessage, String sessionId) {
        try {
            VoiceSessionManager.VoiceSession session = voiceSessionManager.getSession(sessionId);
            VoiceOrder currentOrder = session.getCurrentOrder();
            
            OrderUpdateResult result = OrderUpdateResult.builder()
                    .success(true)
                    .orderUpdated(false)
                    .build();

            // Analyze user message for order actions
            String normalizedMessage = userMessage.toLowerCase().trim();
            
            // Check for add item intent
            if (containsAddIntent(normalizedMessage)) {
                List<OrderAddition> additions = extractOrderAdditions(userMessage);
                for (OrderAddition addition : additions) {
                    addItemToOrder(currentOrder, addition);
                    result.setOrderUpdated(true);
                }
                result.setAction("ADD_ITEMS");
                result.setMessage("Added items to order");
            }
            
            // Check for remove item intent
            else if (containsRemoveIntent(normalizedMessage)) {
                List<String> itemsToRemove = extractItemsToRemove(userMessage);
                for (String itemName : itemsToRemove) {
                    removeItemFromOrder(currentOrder, itemName);
                    result.setOrderUpdated(true);
                }
                result.setAction("REMOVE_ITEMS");
                result.setMessage("Removed items from order");
            }
            
            // Check for modify quantity intent
            else if (containsQuantityChangeIntent(normalizedMessage)) {
                List<QuantityChange> changes = extractQuantityChanges(userMessage);
                for (QuantityChange change : changes) {
                    updateItemQuantity(currentOrder, change);
                    result.setOrderUpdated(true);
                }
                result.setAction("UPDATE_QUANTITIES");
                result.setMessage("Updated item quantities");
            }
            
            // Check for customization intent
            else if (containsCustomizationIntent(normalizedMessage)) {
                List<CustomizationRequest> customizations = extractCustomizations(userMessage);
                for (CustomizationRequest customization : customizations) {
                    addCustomizationToOrder(currentOrder, customization);
                    result.setOrderUpdated(true);
                }
                result.setAction("ADD_CUSTOMIZATIONS");
                result.setMessage("Added customizations to order");
            }
            
            // Check for order finalization intent
            else if (containsFinalizationIntent(normalizedMessage)) {
                calculateOrderTotals(currentOrder);
                result.setAction("FINALIZE_ORDER");
                result.setMessage("Order ready for checkout");
                result.setOrderUpdated(true);
            }

            // Update session if order was modified
            if (result.isOrderUpdated()) {
                voiceSessionManager.updateOrderContext(sessionId, currentOrder);
            }

            return result;

        } catch (Exception e) {
            log.error("Error processing order intent for session: {}", sessionId, e);
            return OrderUpdateResult.builder()
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Get current order summary
     */
    public String getOrderSummary(String sessionId) {
        try {
            VoiceSessionManager.VoiceSession session = voiceSessionManager.getSession(sessionId);
            VoiceOrder order = session.getCurrentOrder();
            
            if (order.getItems().isEmpty()) {
                return "Your order is currently empty.";
            }
            
            StringBuilder summary = new StringBuilder();
            summary.append("ORDER SUMMARY:\n\n");
            
            for (VoiceOrderItem item : order.getItems()) {
                summary.append("• ").append(item.getQuantity())
                       .append("x ").append(item.getName());
                
                if (item.getPrice() != null) {
                    summary.append(" - $").append(item.getPrice());
                }
                
                if (!item.getCustomizations().isEmpty()) {
                    summary.append("\n  Customizations: ")
                           .append(String.join(", ", item.getCustomizations()));
                }
                
                if (item.getNotes() != null && !item.getNotes().trim().isEmpty()) {
                    summary.append("\n  Notes: ").append(item.getNotes());
                }
                
                summary.append("\n");
            }
            
            if (order.getSubtotal() != null) {
                summary.append("\nSubtotal: $").append(String.format("%.2f", order.getSubtotal()));
            }
            
            if (order.getTax() != null) {
                summary.append("\nTax: $").append(String.format("%.2f", order.getTax()));
            }
            
            if (order.getTotal() != null) {
                summary.append("\nTotal: $").append(String.format("%.2f", order.getTotal()));
            }
            
            if (order.getSpecialInstructions() != null && !order.getSpecialInstructions().trim().isEmpty()) {
                summary.append("\nSpecial Instructions: ").append(order.getSpecialInstructions());
            }
            
            return summary.toString();
            
        } catch (Exception e) {
            log.error("Error getting order summary for session: {}", sessionId, e);
            return "Unable to retrieve order summary.";
        }
    }

    // Helper methods for intent analysis
    private boolean containsAddIntent(String message) {
        String[] addKeywords = {"add", "order", "get", "i want", "i'll have", "can i get", "i'd like"};
        return Arrays.stream(addKeywords).anyMatch(message::contains);
    }

    private boolean containsRemoveIntent(String message) {
        String[] removeKeywords = {"remove", "delete", "cancel", "take off", "don't want", "no more"};
        return Arrays.stream(removeKeywords).anyMatch(message::contains);
    }

    private boolean containsQuantityChangeIntent(String message) {
        String[] quantityKeywords = {"change", "update", "make it", "instead of", "more", "less"};
        return Arrays.stream(quantityKeywords).anyMatch(message::contains) && 
               message.matches(".*\\b\\d+\\b.*");
    }

    private boolean containsCustomizationIntent(String message) {
        String[] customizationKeywords = {"with", "without", "no", "extra", "add", "hold", "on the side"};
        return Arrays.stream(customizationKeywords).anyMatch(message::contains);
    }

    private boolean containsFinalizationIntent(String message) {
        String[] finalizeKeywords = {"that's all", "checkout", "pay", "finish", "complete", "done", "ready"};
        return Arrays.stream(finalizeKeywords).anyMatch(message::contains);
    }

    // Helper methods for extraction (simplified versions)
    private Set<String> extractMentionedCategories(String message) {
        Set<String> categories = new HashSet<>();
        String[] commonCategories = {"appetizer", "appetizers", "main", "mains", "entree", "entrees", 
                                   "dessert", "desserts", "drink", "drinks", "beverage", "beverages"};
        
        for (String category : commonCategories) {
            if (message.toLowerCase().contains(category)) {
                categories.add(category);
            }
        }
        return categories;
    }

    private Set<String> extractMentionedItems(String message) {
        // This would be more sophisticated in production, possibly using NLP
        Set<String> items = new HashSet<>();
        List<MenuItem> allItems = menuItemRepository.findByIsAvailable(true);
        
        for (MenuItem item : allItems) {
            if (message.toLowerCase().contains(item.getName().toLowerCase())) {
                items.add(item.getName());
            }
        }
        return items;
    }

    private List<MenuItem> findMenuItemsByName(String itemName) {
        return menuItemRepository.findByNameContainingIgnoreCaseAndIsAvailable(itemName, true);
    }

    private List<OrderAddition> extractOrderAdditions(String message) {
        // Simplified extraction - in production, use more sophisticated NLP
        List<OrderAddition> additions = new ArrayList<>();
        // This would analyze the message and extract items with quantities
        return additions;
    }

    private List<String> extractItemsToRemove(String message) {
        // Simplified extraction
        return new ArrayList<>();
    }

    private List<QuantityChange> extractQuantityChanges(String message) {
        return new ArrayList<>();
    }

    private List<CustomizationRequest> extractCustomizations(String message) {
        return new ArrayList<>();
    }

    private void addItemToOrder(VoiceOrder order, OrderAddition addition) {
        // Add item to order logic
    }

    private void removeItemFromOrder(VoiceOrder order, String itemName) {
        order.getItems().removeIf(item -> item.getName().equalsIgnoreCase(itemName));
    }

    private void updateItemQuantity(VoiceOrder order, QuantityChange change) {
        // Update quantity logic
    }

    private void addCustomizationToOrder(VoiceOrder order, CustomizationRequest customization) {
        // Add customization logic
    }

    private void calculateOrderTotals(VoiceOrder order) {
        double subtotal = order.getItems().stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
        
        double tax = subtotal * 0.0825; // 8.25% tax rate
        double total = subtotal + tax;
        
        order.setSubtotal(subtotal);
        order.setTax(tax);
        order.setTotal(total);
    }

    /**
     * Build customization context for a menu item
     */
    private String buildCustomizationContext(MenuItem menuItem) {
        try {
            List<Customization> customizations = customizationRepository.findByMenuItemId(menuItem.getId());
            
            if (customizations.isEmpty()) {
                return "";
            }
            
            StringBuilder context = new StringBuilder();
            context.append("\n  Customizations:\n");
            
            // Group customizations by group name
            Map<String, List<Customization>> customizationsByGroup = customizations.stream()
                    .collect(Collectors.groupingBy(
                            customization -> customization.getGroupName() != null ? 
                                    customization.getGroupName() : "Options"
                    ));
            
            for (Map.Entry<String, List<Customization>> entry : customizationsByGroup.entrySet()) {
                String groupName = entry.getKey();
                List<Customization> groupCustomizations = entry.getValue();
                
                // Determine choice type from first customization in group
                String choiceType = groupCustomizations.get(0).getChoiceType() != null ? 
                        groupCustomizations.get(0).getChoiceType() : "single";
                
                context.append("    • ").append(groupName)
                       .append(" (").append(choiceType).append(" choice): ");
                
                List<String> options = new ArrayList<>();
                for (Customization customization : groupCustomizations) {
                    StringBuilder option = new StringBuilder();
                    option.append(customization.getName());
                    
                    if (customization.getPrice() != null &&
                        customization.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                        option.append(" (+$").append(customization.getPrice()).append(")");
                    } else if (customization.getPrice() != null &&
                              customization.getPrice().compareTo(BigDecimal.ZERO) == 0) {
                        option.append(" (free)");
                    }
                    
                    if (customization.getIsDefault() != null && customization.getIsDefault()) {
                        option.append(" (default)");
                    }
                    
                    options.add(option.toString());
                }
                
                context.append(String.join(", ", options)).append("\n");
            }
            
            return context.toString();
            
        } catch (Exception e) {
            log.warn("Error building customization context for menu item {}: {}", 
                    menuItem.getId(), e.getMessage());
            return "";
        }
    }

    // Data classes for order processing
    @lombok.Builder
    @lombok.Data
    public static class OrderUpdateResult {
        private boolean success;
        private boolean orderUpdated;
        private String action;
        private String message;
        private String error;
    }

    @lombok.Data
    public static class OrderAddition {
        private String itemName;
        private Integer quantity;
        private List<String> customizations;
    }

    @lombok.Data
    public static class QuantityChange {
        private String itemName;
        private Integer newQuantity;
    }

    @lombok.Data
    public static class CustomizationRequest {
        private String itemName;
        private List<String> customizations;
    }
}