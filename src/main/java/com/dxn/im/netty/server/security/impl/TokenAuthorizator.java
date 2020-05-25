package com.dxn.im.netty.server.security.impl;

import com.dxn.im.netty.server.security.IAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TokenAuthorizator implements IAuthenticator {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    public boolean authenticate(String clientId, String token) {
        return true;
    }
}
