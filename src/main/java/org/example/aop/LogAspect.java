package org.example.aop;

import org.example.annotation.After;
import org.example.annotation.Aspect;
import org.example.annotation.Before;
import org.example.annotation.Service;

import java.lang.reflect.Method;

@Aspect
@Service
public class LogAspect {
    @Before(".*Service.*")
    public void logBefore(Method method) {
        System.out.println("前置增强 "+method.getDeclaringClass().getName()+"#" + method.getName());
    }

    @After(".*Service.*")
    public void logAfter(Method method) {
        System.out.println("后置增强"+method.getDeclaringClass().getName() + "#" + method.getName());
    }
}
