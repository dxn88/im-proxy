package com.dxn.im.netty.server.protocol;

import com.dxn.im.netty.server.bean.Constants;
import com.dxn.im.netty.server.bean.Topic;
import com.dxn.im.netty.server.connection.ConnectionDescriptor;
import com.dxn.im.netty.server.connection.IConnectionsManager;
import com.dxn.im.netty.server.handler.AnswerPublishHandler;
import com.dxn.im.netty.server.security.IAuthenticator;
import com.dxn.im.util.NettyUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.dxn.im.netty.server.connection.ConnectionDescriptor.ConnectionState.*;
import static com.dxn.im.netty.server.protocol.ProtocolProcessorUtil.*;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.*;
import static io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader.from;
import static io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE;
import static io.netty.handler.codec.mqtt.MqttQoS.FAILURE;

@Service
public class ProtocolProcessor {

    static final class WillMessage {

        private final String topic;
        private final ByteBuffer payload;
        private final boolean retained;
        private final MqttQoS qos;

        WillMessage(String topic, ByteBuffer payload, boolean retained, MqttQoS qos) {
            this.topic = topic;
            this.payload = payload;
            this.retained = retained;
            this.qos = qos;
        }

        public String getTopic() {
            return topic;
        }

        public ByteBuffer getPayload() {
            return payload;
        }

        public boolean isRetained() {
            return retained;
        }

        public MqttQoS getQos() {
            return qos;
        }
    }

    private enum SubscriptionState {
        STORED, VERIFIED
    }

    private class RunningSubscription {

        final String clientID;
        final long packetId;

        RunningSubscription(String clientID, long packetId) {
            this.clientID = clientID;
            this.packetId = packetId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            RunningSubscription that = (RunningSubscription) o;

            return packetId == that.packetId
                    && (clientID != null ? clientID.equals(that.clientID) : that.clientID == null);
        }

        @Override
        public int hashCode() {
            int result = clientID != null ? clientID.hashCode() : 0;
            result = 31 * result + (int) (packetId ^ (packetId >>> 32));
            return result;
        }
    }

    @Autowired
    protected IConnectionsManager connectionDescriptors;
    //    protected ConcurrentMap<RunningSubscription, SubscriptionState> subscriptionInCourse;
    // todo 所有的配置做成配置文件形式
    // 是否允许匿名访问
    private boolean allowAnonymous;
    // 允许clientId为空访问
    private boolean allowZeroByteClientId;
    // 读写权限，暂时不做处理
//    private IAuthorizator m_authorizator;

    //    private ISessionsStore m_sessionsStore;
    @Autowired
    private IAuthenticator authenticator;
    // 消息拦截器处理，暂不做处理
//    private BrokerInterceptor m_interceptor;

    //    private Qos2PublishHandler qos2PublishHandler;
    @Autowired
    private AnswerPublishHandler answerPublishHandler;
//    private MessagesPublisher messagesPublisher;
//    private InflightMessageHandler inflightMessageHandler;
    private Logger log = LoggerFactory.getLogger(this.getClass());

    ProtocolProcessor() {
    }

    public void processConnect(Channel channel, MqttConnectMessage msg) {
        MqttConnectPayload payload = msg.payload();
        String clientId = payload.clientIdentifier();
        // 暂时不允许clientId为空链接
        if (!StringUtils.hasLength(clientId)) {
            ProtocolProcessorUtil.closeChannelAndLog(channel, clientId, CONNECTION_REFUSED_IDENTIFIER_REJECTED);
            return;
        }
        // 协议验证
        if (isBadProtocol(channel, clientId, msg)) {
            return;
        }
        // 登陆验证
        if (!login(msg)) {
            ProtocolProcessorUtil.closeChannelAndLog(channel, clientId, CONNECTION_REFUSED_NOT_AUTHORIZED);
            return;
        }
        // 会话管理
        ConnectionDescriptor descriptor = new ConnectionDescriptor(clientId, channel,
                msg.variableHeader().isCleanSession());
        ConnectionDescriptor preConnection = this.connectionDescriptors.addConnection(descriptor);
        // 关闭之前的链接
        if (preConnection != null) {
            closeWithMessage(preConnection);
        }
        // 初始化心跳handler
        initializeKeepAliveTimeout(channel, msg, clientId);
        // 发送connectAck给客户端
        if (!sendAck(descriptor, msg, clientId)) {
            channel.close();
            connectionDescriptors.removeConnection(descriptor);
            return;
        }
        // todo  对消息拦截处理，暂时不做

        // 设置自动刷新
        if (!republish(descriptor, msg)) {
            channel.close();
            connectionDescriptors.removeConnection(descriptor);
            return;
        }

        final boolean success = descriptor.assignState(MESSAGES_REPUBLISHED, ESTABLISHED);
        if (!success) {
            channel.close();
            connectionDescriptors.removeConnection(descriptor);
        }
        // todo 用户链接成功，发送用户登录给Broker，sendRegUserStatus(clientId, EnumConnectStat.CONNECT)

        // 初始化channel.attr属性
        NettyUtils.app(channel, payload.willTopic());
        NettyUtils.imId(channel, payload.willMessage());
        NettyUtils.token(channel, payload.userName());
    }

    private boolean isBadProtocol(Channel channel, String clientId, MqttConnectMessage msg) {
        MqttConnectPayload payload = msg.payload();

        if (msg.variableHeader().version() != MqttVersion.MQTT_3_1.protocolLevel()
                && msg.variableHeader().version() != MqttVersion.MQTT_3_1_1.protocolLevel()) {
            ProtocolProcessorUtil.closeChannelAndLog(channel, clientId, CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION);
            return true;
        }

        if (!msg.variableHeader().isWillFlag() || StringUtil.isNullOrEmpty(payload.willTopic())
                || StringUtil.isNullOrEmpty(payload.willMessage())) {
            ProtocolProcessorUtil.closeChannelAndLog(channel, clientId, CONNECTION_REFUSED_NOT_AUTHORIZED);
            return true;
        }

        return false;
    }

    private boolean login(final MqttConnectMessage msg) {
        MqttConnectPayload payload = msg.payload();
        String clientId = payload.clientIdentifier();
        String userName = payload.userName();
        // 当clientId为空的时候，判断是否允许匿名登录
        if (!StringUtils.hasLength(clientId)) {
            return this.allowAnonymous;
        }
        // userName存储token
        if (!StringUtils.hasLength(userName)) {
            return this.allowAnonymous;
        }
        // clientId和token都不为空，进行验证
        return authenticator.authenticate(clientId, userName);
    }

    private boolean sendAck(ConnectionDescriptor descriptor, MqttConnectMessage msg, final String clientId) {
        final boolean success = descriptor.assignState(DISCONNECTED, SENDACK);
        if (!success) {
            return false;
        }

        MqttConnAckMessage okResp;
        if (!msg.variableHeader().isCleanSession()) {
            okResp = connAckWithSessionPresent(CONNECTION_ACCEPTED);
        } else {
            okResp = connAck(CONNECTION_ACCEPTED);
        }

        descriptor.writeAndFlush(okResp);
        return true;
    }

    private void initializeKeepAliveTimeout(Channel channel, MqttConnectMessage msg, final String clientId) {
        int keepAlive = msg.variableHeader().keepAliveTimeSeconds();
        NettyUtils.keepAlive(channel, keepAlive);
        // session.attr(NettyUtils.ATTR_KEY_CLEANSESSION).set(msg.variableHeader().isCleanSession());
        NettyUtils.cleanSession(channel, msg.variableHeader().isCleanSession());
        // used to track the client in the subscription and publishing phases.
        NettyUtils.clientID(channel, clientId);
        // int idleTime = Math.round(keepAlive * 1.5f);
        int idleTime = (int) (keepAlive * 1.5);
        setIdleTime(channel.pipeline(), idleTime);
    }

    private boolean republish(ConnectionDescriptor descriptor, MqttConnectMessage msg) {
        final boolean success = descriptor.assignState(SENDACK, MESSAGES_REPUBLISHED);
        if (!success) {
            return false;
        }

        int flushIntervalMs = 500/* (keepAlive * 1000) / 2 */;
        descriptor.setupAutoFlusher(flushIntervalMs);
        return true;
    }

    private void setIdleTime(ChannelPipeline pipeline, int idleTime) {
        if (pipeline.names().contains("idleStateHandler")) {
            pipeline.remove("idleStateHandler");
        }
        pipeline.addFirst("idleStateHandler", new IdleStateHandler(idleTime, 0, 0));
    }

    public void processPubAck(Channel channel, MqttPubAckMessage msg) {
        int messageID = msg.variableHeader().messageId();
        String clientID = NettyUtils.clientID(channel);
    }

    public void processPublish(Channel channel, MqttPublishMessage msg) {
        final MqttQoS qos = msg.fixedHeader().qosLevel();

        switch (qos) {
            case AT_MOST_ONCE:
                this.answerPublishHandler.receivedPublish(channel, msg);
                break;
            case AT_LEAST_ONCE:
                this.answerPublishHandler.receivedPublish(channel, msg);
                break;
            case EXACTLY_ONCE:
                this.answerPublishHandler.receivedPublish(channel, msg);
                break;
            default:
                log.error("Unknown QoS-Type:" + qos);
                break;
        }
    }

    /**
     * Second phase of a publish QoS2 protocol, sent by publisher to the broker.
     * Search the stored message and publish to all interested subscribers.
     *
     * @param channel the channel of the incoming message.
     * @param msg     the decoded pubrel message.
     */
    public void processPubRel(Channel channel, MqttMessage msg) {
    }

    public void processPubRec(Channel channel, MqttMessage msg) {
    }

    public void processPubComp(Channel channel, MqttMessage msg) {

    }

    public void processDisconnect(Channel channel) throws InterruptedException {
        channel.flush();

        final String clientID = NettyUtils.clientID(channel);
        if (clientID == null) {
            return;
        }

        final ConnectionDescriptor existingDescriptor = this.connectionDescriptors.getConnection(clientID);
        if (existingDescriptor == null) {
            // another client with same ID removed the descriptor, we must exit
            channel.close();
            return;
        }

        if (existingDescriptor.doesNotUseChannel(channel)) {
            // another client saved it's descriptor, exit
            log.info("Another client is using the connection descriptor. Closing connection. CId=" + clientID);
            existingDescriptor.abort();
            return;
        }

        existingDescriptor.abort();

        boolean stillPresent = this.connectionDescriptors.removeConnection(existingDescriptor);
        if (!stillPresent) {
            // another descriptor was inserted
            log.info("Another descriptor has been inserted. CId=" + clientID);
            return;
        }
    }

    public void processConnectionLost(String clientID, Channel channel) {
        // monitorLog.log("Processing connection lost event. CId=" + clientID);
        ConnectionDescriptor oldConnDescr = new ConnectionDescriptor(clientID, channel, true);
        connectionDescriptors.removeConnection(oldConnDescr);
    }

    public void processUnsubscribe(Channel channel, MqttUnsubscribeMessage msg) {
        List<String> topics = msg.payload().topics();
        String clientID = NettyUtils.clientID(channel);
        // topics=" + JsonUtil.toJson(topics));
        // ack the client
        int messageID = msg.variableHeader().messageId();
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        MqttUnsubAckMessage ackMessage = new MqttUnsubAckMessage(fixedHeader, from(messageID));
        // topics=" + JsonUtil.toJson(topics)
        // + ", messageId=" + messageID);
        channel.writeAndFlush(ackMessage);
    }

    public void processSubscribe(Channel channel, MqttSubscribeMessage msg) {
        String clientID = NettyUtils.clientID(channel);
        int messageID = messageId(msg);

        String username = NettyUtils.userName(channel);
        List<MqttTopicSubscription> ackTopics = doVerify(clientID, username, msg);
        MqttSubAckMessage ackMessage = doAckMessageFromValidateFilters(ackTopics, messageID);

        channel.writeAndFlush(ackMessage);
    }

    private List<MqttTopicSubscription> doVerify(String clientID, String username, MqttSubscribeMessage msg) {
        List<MqttTopicSubscription> ackTopics = new ArrayList<>();

        final int messageId = messageId(msg);
        for (MqttTopicSubscription req : msg.payload().topicSubscriptions()) {
            Topic topic = new Topic(req.topicName());
            MqttQoS qos;
            if (topic.isValid()) {
                qos = req.qualityOfService();
            } else {
                log.error("Topic filter is not valid CId=" + clientID + ", username=" + username
                        + ", messageId=" + messageId + ", topic=" + topic);
                qos = FAILURE;
            }
            ackTopics.add(new MqttTopicSubscription(topic.toString(), qos));
        }
        return ackTopics;
    }

    /**
     * Create the SUBACK response from a list of topicFilters
     */
    private MqttSubAckMessage doAckMessageFromValidateFilters(List<MqttTopicSubscription> topicFilters, int messageId) {
        List<Integer> grantedQoSLevels = new ArrayList<>();
        for (MqttTopicSubscription req : topicFilters) {
            grantedQoSLevels.add(req.qualityOfService().value());
        }

        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.SUBACK, false, AT_MOST_ONCE, false, 0);
        MqttSubAckPayload payload = new MqttSubAckPayload(grantedQoSLevels);
        return new MqttSubAckMessage(fixedHeader, from(messageId), payload);
    }

    public void notifyChannelWritable(Channel channel) {
    }

    public void closeWithMessage(String clientId) {
        ConnectionDescriptor descriptor = this.connectionDescriptors.getConnection(clientId);
        if (descriptor == null) {
            return;
        }
        closeWithMessage(descriptor);
    }

    public void closeWithMessage(ConnectionDescriptor descriptor) {
        if (descriptor != null) {

            MqttMessageBuilders.PublishBuilder builder = MqttMessageBuilders.publish();
            builder.topicName("");
            builder.qos(AT_MOST_ONCE);
            builder.retained(false);
            builder.payload(Unpooled.copiedBuffer(Constants.BROKER_DISCONNECTION_MESSAGE.getBytes()));
            MqttPublishMessage message = builder.build();

            descriptor.writeAndFlush(message);
            descriptor.abort();
        }
    }

    public void notifyAllDisconnect() {
        connectionDescriptors.notifyAllDisconnect();
    }

    // 包括自己
    public void pushMsg2All(String pushContent) {
        connectionDescriptors.pushMsg2All(pushContent, null, null);
    }
}
