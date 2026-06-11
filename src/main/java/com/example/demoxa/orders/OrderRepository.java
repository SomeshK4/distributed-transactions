package com.example.demoxa.orders;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Somesh Kumar
 */
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
}
