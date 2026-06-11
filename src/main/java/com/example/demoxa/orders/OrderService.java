package com.example.demoxa.orders;

import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final JmsTemplate jmsTemplate;

    public OrderService(
            OrderRepository repository,
            JmsTemplate jmsTemplate) {

        this.repository = repository;
        this.jmsTemplate = jmsTemplate;
    }

    @Transactional
    public void createOrder(
            String product,
            boolean fail) {

        OrderEntity order = new OrderEntity();
        order.setProduct(product);

        repository.save(order);

        jmsTemplate.convertAndSend(
                "order.queue",
                "created:" + product);

        try{
            Thread.sleep(7000);
        }
        catch(InterruptedException e){
            Thread.currentThread().interrupt();
        }

        if (fail) {
            throw new RuntimeException(
                    "forcing rollback");
        }
    }
}