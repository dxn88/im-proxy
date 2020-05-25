package com.dxn.im.netty.server.connection;

import com.dxn.im.util.NettyUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiPredicate;
import java.util.function.Function;

@Service
public class ConnectionDescriptorManager implements IConnectionsManager {
    private static Logger log = LoggerFactory.getLogger(ConnectionDescriptorManager.class);
    // clientId -> 链接
    private final ConcurrentMap<String, ConnectionDescriptor> connectionDescriptors = new ConcurrentHashMap<>();

    public void sendMessage2Client(MqttMessage message, String clientID) {
        final MqttMessageType messageType = message.fixedHeader().messageType();

        try {
            ConnectionDescriptor descriptor = connectionDescriptors.get(clientID);
            if (descriptor == null) {
                return;
            }

            descriptor.writeAndFlush(message);

        } catch (Throwable e) {
            String errorMsg = "Unable to send " + messageType + " message. CId=<" + clientID + ">";
            log.error(errorMsg, e);
        }
    }

    @Override
    public ConnectionDescriptor addConnection(ConnectionDescriptor descriptor) {
        ConnectionDescriptor previous = connectionDescriptors.get(descriptor.clientID);
        connectionDescriptors.put(descriptor.clientID, descriptor);
        return previous;
    }

    @Override
    public boolean removeConnection(ConnectionDescriptor descriptor) {
        boolean result = connectionDescriptors.remove(descriptor.clientID, descriptor);
        return result;
    }

    @Override
    public ConnectionDescriptor getConnection(String clientID) {
        return connectionDescriptors.get(clientID);
    }

    @Override
    public boolean isConnected(String clientID) {
        return connectionDescriptors.containsKey(clientID);
    }

    @Override
    public int getActiveConnectionsNo() {
        return connectionDescriptors.size();
    }

    @Override
    public Collection<String> getConnectedClientIds() {
        return connectionDescriptors.keySet();
    }

    @Override
    public boolean closeConnection(String clientID, boolean closeImmediately) {
        ConnectionDescriptor descriptor = connectionDescriptors.get(clientID);
        if (descriptor == null) {
            log.error("Connection descriptor doesn't exist. MQTT connection cannot be closed. CId=" + clientID
                    + ",closeImmediately=" + closeImmediately);
            return false;
        }
        if (closeImmediately) {
            descriptor.abort();
            return true;
        } else {
            return descriptor.close();
        }
    }

    public void notifyAllDisconnect() {
        Iterator<Entry<String, ConnectionDescriptor>> iterator = connectionDescriptors.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, ConnectionDescriptor> en = iterator.next();
            ConnectionDescriptor connectionDescriptor = en.getValue();
            connectionDescriptor.abort();
        }
    }

   public void pushMsg2All(String content, String clientId, BiPredicate filter){
       Iterator<Entry<String, ConnectionDescriptor>> iterator = connectionDescriptors.entrySet().iterator();
       while (iterator.hasNext()) {
           Entry<String, ConnectionDescriptor> en = iterator.next();
           Channel channel = en.getValue().getChannel();
           if (filter != null && StringUtils.hasLength(clientId) &&filter.test(NettyUtils.clientID(channel), clientId)) {
               continue;
           }
           try {
               if (NettyUtils.app(channel) != null && NettyUtils.imId(channel) != null) {
                   MqttMessageBuilders.PublishBuilder builder = MqttMessageBuilders.publish();
                   builder.topicName("");
                   builder.qos(MqttQoS.AT_MOST_ONCE);
                   builder.retained(false);
                   builder.payload(Unpooled.copiedBuffer(content.getBytes()));
                   MqttPublishMessage message = builder.build();
                   en.getValue().writeAndFlush(message);
               }
           } catch (Exception e) {
               log.error("push message to CId=" + en.getValue().clientID + " error", e);
           }
       }
    }


}
