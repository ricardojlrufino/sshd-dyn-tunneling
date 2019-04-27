/*
 * *****************************************************************************
 * Copyright (c) 2013-2014 CriativaSoft (www.criativasoft.com.br)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Ricardo JL Rufino - Initial API and Implementation
 * *****************************************************************************
 */

package shuaicj.hobby.http.proxy.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle data from client.
 *
 * @author shuaicj 2017/09/21
 */
public class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {

    private final String id;
    private Channel clientChannel;
    private Channel remoteChannel;

//    private HttpProxyClientHeader header;
    private Logger logger = LoggerFactory.getLogger(HttpProxyClientHandler.class);

    public HttpProxyClientHandler(String id) {
        this.id = id;
//        this.header = new HttpProxyClientHeader();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {

        final Channel inboundChannel = ctx.channel();
        clientChannel = inboundChannel;

        // Start the connection attempt.
        Bootstrap b = new Bootstrap();
        // b.group(inboundChannel.eventLoop()).channel(ctx.channel().getClass())
        b.group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .handler(new HttpProxyRemoteHandler(id, clientChannel))
                .option(ChannelOption.AUTO_READ, false);

        ChannelFuture f = b.connect("191.17.14.7", 8000);
        remoteChannel = f.channel();

        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    // connection complete start to read first data
                    inboundChannel.read();
                } else {
                    // Close the connection if the connection attempt has
                    // failed.Smartphone
                    inboundChannel.close();
                }
            }
        });
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        if (remoteChannel.isActive()) {
			/*
			 * Sends the client request to the backend service.
			 */
            remoteChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        // was able to flush out data, start to read the next
                        // chunk
                        ctx.channel().read();
                    } else {
                        future.channel().close();
                    }
                }
            });
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        flushAndClose(remoteChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        logger.error(id + " shit happens", e);
        flushAndClose(clientChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
