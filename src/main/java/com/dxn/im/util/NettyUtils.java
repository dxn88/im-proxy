package com.dxn.im.util;

import com.dxn.im.netty.server.bean.Constants;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

/**
 * Some Netty's channels utilities.
 */
public final class NettyUtils {

    public static final String ATTR_USERNAME = "username";
    public static final String ATTR_SESSION_STOLEN = "sessionStolen";
    public static final String ATTR_CHANNEL_STATUS = "channelStatus";

    public static final String ATTR_APP = "app";
    public static final String ATTR_TOKEN = "token";
    public static final String ATTR_IMID = "imId";

    private static final AttributeKey<Object> ATTR_KEY_KEEPALIVE = AttributeKey.valueOf(Constants.KEEP_ALIVE);
    private static final AttributeKey<Object> ATTR_KEY_CLEANSESSION = AttributeKey.valueOf(Constants.CLEAN_SESSION);
    private static final AttributeKey<Object> ATTR_KEY_CLIENTID = AttributeKey.valueOf(Constants.ATTR_CLIENTID);
    private static final AttributeKey<Object> ATTR_KEY_USERNAME = AttributeKey.valueOf(ATTR_USERNAME);

    private static final AttributeKey<Object> ATTR_KEY_APP = AttributeKey.valueOf(ATTR_APP);
    private static final AttributeKey<Object> ATTR_KEY_TOKEN = AttributeKey.valueOf(ATTR_TOKEN);
    private static final AttributeKey<Object> ATTR_KEY_IMID = AttributeKey.valueOf(ATTR_IMID);

    public static Object getAttribute(ChannelHandlerContext ctx, AttributeKey<Object> key) {
        Attribute<Object> attr = ctx.channel().attr(key);
        return attr.get();
    }

    public static void keepAlive(Channel channel, int keepAlive) {
        channel.attr(NettyUtils.ATTR_KEY_KEEPALIVE).set(keepAlive);
    }

    public static void cleanSession(Channel channel, boolean cleanSession) {
        channel.attr(NettyUtils.ATTR_KEY_CLEANSESSION).set(cleanSession);
    }

    public static boolean cleanSession(Channel channel) {
        return (Boolean) channel.attr(NettyUtils.ATTR_KEY_CLEANSESSION).get();
    }

    public static void clientID(Channel channel, String clientID) {
        channel.attr(NettyUtils.ATTR_KEY_CLIENTID).set(clientID);
    }

    public static String clientID(Channel channel) {
        return (String) channel.attr(NettyUtils.ATTR_KEY_CLIENTID).get();
    }

    public static void userName(Channel channel, String username) {
        channel.attr(NettyUtils.ATTR_KEY_USERNAME).set(username);
    }

    public static String userName(Channel channel) {
        return (String) channel.attr(NettyUtils.ATTR_KEY_USERNAME).get();
    }

    public static String app(Channel channel) {
        return (String) channel.attr(NettyUtils.ATTR_KEY_APP).get();
    }

    public static void app(Channel channel, String app) {
        channel.attr(NettyUtils.ATTR_KEY_APP).set(app);
    }

    public static String token(Channel channel) {
        return (String) channel.attr(NettyUtils.ATTR_KEY_TOKEN).get();
    }

    public static void token(Channel channel, String token) {
        channel.attr(NettyUtils.ATTR_KEY_TOKEN).set(token);
    }

    public static String imId(Channel channel) {
        return (String) channel.attr(NettyUtils.ATTR_KEY_IMID).get();
    }

    public static void imId(Channel channel, String imId) {
        channel.attr(NettyUtils.ATTR_KEY_IMID).set(imId);
    }

    private NettyUtils() {
    }
}
