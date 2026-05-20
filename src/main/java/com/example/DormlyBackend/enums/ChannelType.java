package com.example.DormlyBackend.enums;

public enum ChannelType {
    EMAIL, SMS, PUSH, WEBSOCKET;

    public String topic() {
        return "notifications." + this.name().toLowerCase();
    }
}
