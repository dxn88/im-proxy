/*
 * Copyright (c) 2012-2017 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package com.dxn.im.netty.server.handler;

import com.dxn.im.util.NettyUtils;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Sharable
@Service
public class ReadTimeoutCloseChannelHandler extends ChannelDuplexHandler {
    private static final Logger log = LoggerFactory.getLogger(AutoFlushHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState e = ((IdleStateEvent) evt).state();
            if (e == IdleState.READER_IDLE) {
                String clientID = NettyUtils.clientID(ctx.channel());
                if (clientID != null) {
                    log.info("HEARTBEATTIMEOUT", clientID, null, null);
                }
                ctx.fireChannelInactive();
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
