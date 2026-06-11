package com.example.demoxa.orders;


import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class OrderListener {

    @JmsListener(destination = "order.queue")
    public void onMessage(String msg) {
        System.out.println("📩 RECEIVED: " + msg);
    }
}