package com.dxn.im.netty.server.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Data
@Component
@PropertySource("classpath:config.properties")
@ConfigurationProperties(prefix = "server")
public class ServerConfig {
    private String ip;
    private Integer tcpPort;
    private Integer tcpSslPort;
    private Integer webSocketPort;
    private Integer webSocketSslPort;
    private Integer httpPort;

}
