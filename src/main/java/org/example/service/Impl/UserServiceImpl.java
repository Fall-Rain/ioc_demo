package org.example.service.Impl;

import org.example.annotation.Autowirte;
import org.example.annotation.Service;
import org.example.service.OrderService;
import org.example.service.UserService;
import org.example.utils.Utils;

@Service
public class UserServiceImpl implements UserService {
    @Autowirte
    private OrderService orderService;
    @Autowirte
    private Utils utils;

    @Override
    public void sss() {
        System.out.println("hello UserService");
    }

    @Override
    public void say() {
        System.out.println("hello");
        orderService.say();
    }
}
