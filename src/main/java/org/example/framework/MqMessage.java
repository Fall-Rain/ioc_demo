package org.example.framework;

import org.example.annotation.Service;

@Service
public class MqMessage implements Message<String> {
    @Override
    public void onMessage(String entity) {

    }
}
