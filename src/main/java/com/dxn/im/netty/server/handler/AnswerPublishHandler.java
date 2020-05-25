package com.dxn.im.netty.server.handler;

import com.dxn.im.netty.server.connection.IConnectionsManager;
import com.dxn.im.netty.server.protocol.ProtocolProcessorUtil;
import com.dxn.im.util.NettyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.BiPredicate;

import static io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader.from;
import static io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE;

@Service
public class AnswerPublishHandler {
    private static Logger log = LoggerFactory.getLogger(ProtocolProcessorUtil.class);

    @Autowired
    private IConnectionsManager connectionsManager;

    public void receivedPublish(Channel channel, MqttPublishMessage msg) {
//        final Topic topic = new Topic(msg.variableHeader().topicName());
        String clientID = NettyUtils.clientID(channel);
//        MqttQoS qos = msg.fixedHeader().qosLevel();
        int packetId = msg.variableHeader().packetId();
        packetId = 1001;
//        if (MqttQoS.AT_LEAST_ONCE == qos) {
        sendPubAck(clientID, packetId);
        // todo 暂时转发给所有链接 除了自己
        connectionsManager.pushMsg2All(convertByteBufToString(msg.payload()), clientID, new BiPredicate() {
            @Override
            public boolean test(Object o, Object o2) {
                return o.equals(o2);
            }
        });
    }


    public String convertByteBufToString(ByteBuf buf) {
        String str;
        if (buf.hasArray()) { // 处理堆缓冲区
            str = new String(buf.array(), buf.arrayOffset() + buf.readerIndex(), buf.readableBytes());
        } else { // 处理直接缓冲区以及复合缓冲区
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            str = new String(bytes, 0, buf.readableBytes());
        }
        return str;
    }

    private void sendPubAck(String clientId, int packetId) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBACK, false, AT_MOST_ONCE, false, 0);
        MqttPubAckMessage pubAckMessage = new MqttPubAckMessage(fixedHeader, from(packetId));

        try {
            if (connectionsManager == null) {
                throw new RuntimeException("Internal bad error, found connectionDescriptors to null while it should "
                        + "be initialized, somewhere it's overwritten!!");
            }
            if (!connectionsManager.isConnected(clientId)) {
                throw new RuntimeException(String.format("Can't find a ConnectionDescriptor for client %s in cache %s",
                        clientId, connectionsManager));
            }

            connectionsManager.sendMessage2Client(pubAckMessage, clientId);
        } catch (Throwable t) {
            log.error("send puback error.", t);
        }
    }
}
