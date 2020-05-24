package com.dxn.im;

import com.dxn.im.netty.server.GateWayServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ImProxyGatewayApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext run = SpringApplication.run(ImProxyGatewayApplication.class, args);
        GateWayServer bean = run.getBean(GateWayServer.class);
        bean.startServer();

    }

}
