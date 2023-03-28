package org.example.framework;

import org.example.annotation.Bean;
import org.example.annotation.Component;
import org.example.annotation.Configuration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

@Configuration
public class ServiceInvocationHandler implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        System.out.println("注册之前：" + beanName);
        return bean;
    }


    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        System.out.println("注册之后：" + beanName);
        return Proxy.newProxyInstance(this.getClass().getClassLoader(), bean.getClass().getInterfaces(), (proxy, method, args) -> {
            System.out.println("前置增强");
            Object result = method.invoke(bean, args);
            System.out.println("后置增强");
            return result;
        });
    }
}
