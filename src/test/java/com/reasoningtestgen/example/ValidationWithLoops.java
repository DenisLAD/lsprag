package com.reasoningtestgen.example;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Пример валидации входных параметров через циклы
 */
public class ValidationWithLoops {

    /**
     * Валидация списка пользователей через forEach
     */
    public void processUsers(List<User> users) {
        // Валидация через forEach
        users.forEach(user -> {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null");
            }
            if (user.getName() == null || user.getName().isEmpty()) {
                throw new IllegalArgumentException("User name cannot be empty");
            }
            if (user.getAge() < 0) {
                throw new IllegalArgumentException("User age cannot be negative");
            }
        });

        // Обработка через stream().filter().forEach()
        users.stream()
            .filter(user -> user.getAge() >= 18)
            .forEach(this::sendNotification);
    }

    /**
     * Валидация через традиционный for цикл
     */
    public double calculateTotal(List<OrderItem> items) {
        double total = 0;
        
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            if (item == null) {
                throw new IllegalArgumentException("Item at index " + i + " is null");
            }
            if (item.getPrice() < 0) {
                throw new IllegalArgumentException("Item price cannot be negative");
            }
            total += item.getPrice() * item.getQuantity();
        }
        
        return total;
    }

    /**
     * Валидация через while цикл
     */
    public List<String> splitIntoBatches(List<String> items, int batchSize) {
        List<String> batches = new ArrayList<>();
        int index = 0;
        
        while (index < items.size()) {
            int end = Math.min(index + batchSize, items.size());
            List<String> batch = items.subList(index, end);
            
            // Валидация батча
            for (String item : batch) {
                if (item == null || item.trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid item in batch");
                }
            }
            
            batches.add(String.join(",", batch));
            index = end;
        }
        
        return batches;
    }

    /**
     * Сложная валидация с вложенными циклами
     */
    public void validateMatrix(List<List<Integer>> matrix) {
        if (matrix == null || matrix.isEmpty()) {
            throw new IllegalArgumentException("Matrix cannot be empty");
        }

        int expectedWidth = matrix.get(0).size();
        
        // Внешний цикл по строкам
        for (List<Integer> row : matrix) {
            if (row == null) {
                throw new IllegalArgumentException("Row cannot be null");
            }
            if (row.size() != expectedWidth) {
                throw new IllegalArgumentException("All rows must have same width");
            }
            
            // Внутренний цикл по столбцам
            for (Integer value : row) {
                if (value == null) {
                    throw new IllegalArgumentException("Cell value cannot be null");
                }
                if (value < 0 || value > 100) {
                    throw new IllegalArgumentException("Cell value must be between 0 and 100");
                }
            }
        }
    }

    private void sendNotification(User user) {
        // Implementation
    }

    // DTO классы
    public static class User {
        private String name;
        private int age;

        public String getName() { return name; }
        public int getAge() { return age; }
    }

    public static class OrderItem {
        private double price;
        private int quantity;

        public double getPrice() { return price; }
        public int getQuantity() { return quantity; }
    }
}
