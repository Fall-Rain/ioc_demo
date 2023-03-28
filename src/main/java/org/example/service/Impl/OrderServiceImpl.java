package org.example.service.Impl;

import org.example.annotation.Autowirte;
import org.example.annotation.Service;
import org.example.service.OrderService;
import org.example.service.UserService;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowirte
    private UserService userService;


    @Override
    public void say() {
        System.out.println("hello OrderService");
        userService.sss();
    }
}
