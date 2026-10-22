package com.grocerymate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GroceryMateApplication {
    public static void main(String[] args) {
        SpringApplication.run(GroceryMateApplication.class, args);
    }
}
