package org.example.framework;

@FunctionalInterface
public interface ObjectFactory<T> {
    T getObject();
}
