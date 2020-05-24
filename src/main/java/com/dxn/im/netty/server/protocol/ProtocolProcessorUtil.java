package com.dxn.im.netty.server.protocol;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ProtocolProcessorUtil {
    private static Logger log = LoggerFactory.getLogger(ProtocolProcessorUtil.class);

    public static int messageId(MqttMessage msg) {
        return ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
    }

    public static void closeChannelAndLog(Channel channel, String clientId, MqttConnectReturnCode returnCode) {
        MqttConnAckMessage badProto = connAck(returnCode);
        log.error("MQTT protocol error ,clientId = {},errorCode = {}", clientId, returnCode.byteValue());
        channel.writeAndFlush(badProto);
        channel.close();
    }

    public static MqttConnAckMessage connAck(MqttConnectReturnCode returnCode) {
        return connAck(returnCode, false);
    }

    public static MqttConnAckMessage connAckWithSessionPresent(MqttConnectReturnCode returnCode) {
        return connAck(returnCode, true);
    }

    private static MqttConnAckMessage connAck(MqttConnectReturnCode returnCode, boolean sessionPresent) {
        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE,
                false, 0);
        MqttConnAckVariableHeader mqttConnAckVariableHeader = new MqttConnAckVariableHeader(returnCode, sessionPresent);
        return new MqttConnAckMessage(mqttFixedHeader, mqttConnAckVariableHeader);
    }
}
