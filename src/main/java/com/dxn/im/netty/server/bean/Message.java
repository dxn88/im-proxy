package com.dxn.im.netty.server.bean;

import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;

import static io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE;

abstract public class Message {
    private static MqttFixedHeader pingHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false, AT_MOST_ONCE, false, 0);
    public static MqttMessage pingResp = new MqttMessage(pingHeader);
}
