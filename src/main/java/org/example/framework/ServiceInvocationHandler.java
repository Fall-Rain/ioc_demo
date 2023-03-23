package org.example.framework;

import org.example.annotation.Bean;
import org.example.annotation.Component;
import org.example.annotation.Configuration;

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
        if (bean instanceof Message) {
            System.out.println(bean.getClass().getName());
            Type genericInterfaces = bean.getClass().getGenericSuperclass();
        }
        return bean;
    }
}
