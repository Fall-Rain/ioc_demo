package org.example.service.Impl;

import org.example.annotation.Autowirte;
import org.example.annotation.Service;
import org.example.service.OrderService;
import org.example.service.UserService;
import org.example.utils.Utils;

@Service
public class UserServiceImpl implements UserService {

    private OrderService orderService;

    private Utils utils;

    @Autowirte
    public UserServiceImpl(OrderService orderService, Utils utils) {
        this.orderService = orderService;
        this.utils = utils;
    }

    public UserServiceImpl(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void say() {
        System.out.println("hello UserService");
        orderService.say();
        utils.sss();
    }
}
