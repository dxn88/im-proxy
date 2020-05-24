package com.dxn.im.netty.server.bean;

/**
 * Server constants keeper
 */
public final class Constants {

    public static final String ATTR_CLIENTID = "ClientID";
    public static final String CLEAN_SESSION = "cleanSession";
    public static final String KEEP_ALIVE = "keepAlive";
    public static final int MAX_MESSAGE_QUEUE = 1024; // number of messages
    public static final String BROKER_DISCONNECTION_MESSAGE = "{\"action\":\"beExited\"}";
    public static final String BROKER_RECONNECT_MESSAGE = "{\"action\":\"reconnect\"}";
    private Constants() {
    }
}
