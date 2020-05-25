package com.dxn.im.netty.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Data
@Component
@PropertySource("classpath:config.properties")
@ConfigurationProperties(prefix = "netty")
public class NettyConfig {
    private Integer backlog;
    private String reuseAddr;
    private String tcpNodelay;
    private String keepalive;
    private String epoll;
    private String webSocketInit;
    private String tcpInit;
}
