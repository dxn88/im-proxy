package com.dxn.im.netty.server;

import com.alibaba.fastjson.JSON;
import com.dxn.im.netty.server.config.ServerConfig;
import com.dxn.im.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GateWayServer {
    private Logger log = LoggerFactory.getLogger(this.getClass());
    private volatile boolean initialized = false;

    @Autowired
    private NettyServer nettyServer;

    public synchronized void startServer() {
        if (initialized == true) return;
        log.info("Start server time = {},config = {}", TimeUtil.getCurrentTime());
        initEnv();
        nettyServer.init();
        initialized = true;
        log.info("Start server success time = {}", TimeUtil.getCurrentTime());
    }

    protected void initEnv() {
        // jvm参数 -Dkey=value等参数，获取 String configPath = System.getProperty("catalina.base", null);
    }

    public synchronized void stopServer() {
        // todo
    }
}
