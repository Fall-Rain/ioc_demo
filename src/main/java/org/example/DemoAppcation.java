package org.example;


import org.example.framework.Application;
import org.example.service.UserService;

public class DemoAppcation {
    public static void main(String[] args) throws Exception {
        Application application = Application.start(DemoAppcation.class);
        UserService userService = application.getBean(UserService.class);
        userService.say();
    }
}
