package com.dxn.im.netty.server;

import com.alibaba.fastjson.JSON;
import com.dxn.im.netty.server.config.NettyConfig;
import com.dxn.im.netty.server.config.ServerConfig;
import com.dxn.im.netty.server.handler.GateWayMessageHandler;
import com.dxn.im.netty.server.handler.ReadTimeoutCloseChannelHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NettyServer {
    private Logger log = LoggerFactory.getLogger(this.getClass());
    private static final String MQTT_VERSION_LIST = "mqtt, mqttv3.1, mqttv3.1.1";

    @Autowired
    private NettyConfig nettyConfig;
    @Autowired
    private ServerConfig serverConfig;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Class<? extends ServerSocketChannel> channelClass;

    @Autowired
    private GateWayMessageHandler gateWayMessageHandler;
    @Autowired
    private ReadTimeoutCloseChannelHandler readTimeoutCloseChannelHandler;

    public void init() {
        log.info("serverConfig = {}, nettyConfig = {}", JSON.toJSON(serverConfig), JSON.toJSON(nettyConfig));
        initGroup();
        initializeWebSocketTransport();
    }

    private void initGroup() {
        if (Boolean.parseBoolean(nettyConfig.getEpoll())) {
            log.info("Netty is using Epoll");
            bossGroup = new EpollEventLoopGroup();
            workerGroup = new EpollEventLoopGroup();
            channelClass = EpollServerSocketChannel.class;
        } else {
            log.info("Netty is using NIO");
            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();
            channelClass = NioServerSocketChannel.class;
        }
    }

    abstract class PipelineInitializer {
        abstract void init(ChannelPipeline pipeline) throws Exception;
    }

    static class WebSocketFrameToByteBufDecoder extends MessageToMessageDecoder<BinaryWebSocketFrame> {

        @Override
        protected void decode(ChannelHandlerContext chc, BinaryWebSocketFrame frame, List<Object> out)
                throws Exception {
            // convert the frame to a ByteBuf
            ByteBuf bb = frame.content();
            // System.out.println("WebSocketFrameToByteBufDecoder decode - " +
            // ByteBufUtil.hexDump(bb));
            bb.retain();
            out.add(bb);
        }
    }

    static class ByteBufToWebSocketFrameEncoder extends MessageToMessageEncoder<ByteBuf> {

        @Override
        protected void encode(ChannelHandlerContext chc, ByteBuf bb, List<Object> out) throws Exception {
            // convert the ByteBuf to a WebSocketFrame
            BinaryWebSocketFrame result = new BinaryWebSocketFrame();
            // System.out.println("ByteBufToWebSocketFrameEncoder encode - " +
            // ByteBufUtil.hexDump(bb));
            result.content().writeBytes(bb);
            out.add(result);
        }
    }

    private void initializeWebSocketTransport() {
        if (!Boolean.parseBoolean(nettyConfig.getWebSocketInit())) {
            return;
        }
        log.info("Init websocket server");
        initFactory(serverConfig.getIp(), serverConfig.getWebSocketPort(), "Websocket MQTT", new PipelineInitializer() {

            @Override
            void init(ChannelPipeline pipeline) {
                pipeline.addLast(new HttpServerCodec());
                pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
                pipeline.addLast("webSocketHandler",
                        new WebSocketServerProtocolHandler("/mqtt", MQTT_VERSION_LIST));
                pipeline.addLast("ws2bytebufDecoder", new WebSocketFrameToByteBufDecoder());
                pipeline.addLast("bytebuf2wsEncoder", new ByteBufToWebSocketFrameEncoder());
                pipeline.addFirst("idleStateHandler", new IdleStateHandler(10, 0, 0));
                pipeline.addAfter("idleStateHandler", "idleEventHandler", readTimeoutCloseChannelHandler);
                pipeline.addLast("decoder", new MqttDecoder());
                pipeline.addLast("encoder", MqttEncoder.INSTANCE);
                pipeline.addLast("handler", gateWayMessageHandler);
            }
        });
    }

    private void initFactory(String host, int port, String protocol, final PipelineInitializer pipeliner) {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup).channel(channelClass).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                try {
                    pipeliner.init(pipeline);
                } catch (Throwable th) {
                    log.error("Severe error during pipeline creation", th);
                    throw th;
                }
            }
        }).option(ChannelOption.SO_BACKLOG, nettyConfig.getBacklog())
                .option(ChannelOption.SO_REUSEADDR, Boolean.parseBoolean(nettyConfig.getReuseAddr()))
                .option(ChannelOption.TCP_NODELAY, Boolean.parseBoolean(nettyConfig.getTcpNodelay()))
                .childOption(ChannelOption.SO_KEEPALIVE, Boolean.parseBoolean(nettyConfig.getKeepalive()));
        try {
            log.info("Binding server. host={" + host + "}, port={" + port + "}");
            // Bind and start to accept incoming connections.
            ChannelFuture channelFuture = bootstrap.bind(host, port);
            log.info("Server has been bound. host={" + host + "}, port={" + port + "}");
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException ex) {
            log.error(
                    "An interruptedException was caught while initializing server. Protocol={" + protocol + "}", ex);
        }
    }

}
