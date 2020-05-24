package com.dxn.im.netty.server.handler;

import com.dxn.im.netty.server.protocol.ProtocolProcessor;
import com.dxn.im.util.NettyUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE;

@Service
@ChannelHandler.Sharable
public class GateWayMessageHandler extends ChannelInboundHandlerAdapter {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ProtocolProcessor processor;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        MqttMessage msg = (MqttMessage) message;
        MqttMessageType messageType = msg.fixedHeader().messageType();
        log.debug("Processing MQTT message, type={" + messageType + "}");
        try {
            switch (messageType) {
                case CONNECT:
                    processor.processConnect(ctx.channel(), (MqttConnectMessage) msg);
                    break;
                case SUBSCRIBE:
                    processor.processSubscribe(ctx.channel(), (MqttSubscribeMessage) msg);
                    break;
                case UNSUBSCRIBE:
                    processor.processUnsubscribe(ctx.channel(), (MqttUnsubscribeMessage) msg);
                    break;
                case PUBLISH:
                    processor.processPublish(ctx.channel(), (MqttPublishMessage) msg);
                    break;
                case PUBREC:
                    processor.processPubRec(ctx.channel(), msg);
                    break;
                case PUBCOMP:
                    processor.processPubComp(ctx.channel(), msg);
                    break;
                case PUBREL:
                    processor.processPubRel(ctx.channel(), msg);
                    break;
                case DISCONNECT:
                    processor.processDisconnect(ctx.channel());
                    break;
                case PUBACK:
                    processor.processPubAck(ctx.channel(), (MqttPubAckMessage) msg);
                    break;
                case PINGREQ:
                    MqttFixedHeader pingHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false, AT_MOST_ONCE, false,
                            0);
                    MqttMessage pingResp = new MqttMessage(pingHeader);
                    ctx.writeAndFlush(pingResp);
                    break;
                default:
                    log.error("Unkonwn MessageType:{" + messageType + "}");
                    break;
            }
        } catch (Throwable ex) {
            log.error("Exception was caught while processing MQTT message, type={" + messageType + "}," + ex.getCause(), ex);
            ctx.fireExceptionCaught(ex);
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientID = NettyUtils.clientID(ctx.channel());
        if (clientID != null && !clientID.isEmpty()) {
            log.info("Notifying connection lost event. MqttClientId = {" + clientID + "}.");
            processor.processConnectionLost(clientID, ctx.channel());
        }
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.info("An unexpected exception was caught while processing MQTT message. Closing Netty channel. CId={"
                + NettyUtils.clientID(ctx.channel()) + "}, " + "cause={" + cause.getCause() + "}, errorMessage={"
                + cause.getMessage() + "}", cause);
        ctx.close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            processor.notifyChannelWritable(ctx.channel());
        }
        ctx.fireChannelWritabilityChanged();
    }
}
