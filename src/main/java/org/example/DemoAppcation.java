package org.example;


import org.example.framework.Applicatio2;
import org.example.service.UserService;

public class DemoAppcation {
    public static void main(String[] args) throws Exception {
        Applicatio2 application = Applicatio2.start(DemoAppcation.class);
        UserService userService = application.getBean("UserServiceImpl");
        userService.say();
//        application.getBean(UserService.class).say();
    }
}
