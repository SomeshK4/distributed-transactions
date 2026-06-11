package com.example.demoxa.orders;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(
            OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> create(
            @RequestParam(name = "product") String product,
            @RequestParam(name = "fail", defaultValue = "false")
            boolean fail) {

        service.createOrder(product, fail);

        return new ResponseEntity<>(HttpStatusCode.valueOf(HttpStatus.OK.value()));
    }
}