package org.example.service.Impl;

import org.example.annotation.Autowirte;
import org.example.annotation.Service;
import org.example.service.OrderService;
import org.example.service.UserService;

@Service
public class UserServiceImpl implements UserService {
    //    @Autowirte
    private OrderService orderService;

    public UserServiceImpl(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void say() {
        System.out.println("UserServiceImpl say");
        orderService.say();
    }

    @Override
    public void processUser() {
        System.out.println("UserServiceImpl processUser");
    }
}
