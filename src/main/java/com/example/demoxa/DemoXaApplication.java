package com.example.demoxa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableJms
@EnableTransactionManagement
public class DemoXaApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoXaApplication.class, args);
    }

}
