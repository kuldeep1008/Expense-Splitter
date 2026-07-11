package com.example.expensesplitter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExpenseSplitterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseSplitterApplication.class, args);
    }
}
