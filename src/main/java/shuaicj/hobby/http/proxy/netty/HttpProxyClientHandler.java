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

        // Start the connection attempt.
        Bootstrap b = new Bootstrap();
        // b.group(inboundChannel.eventLoop()).channel(ctx.channel().getClass())
        b.group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .handler(new HttpProxyRemoteHandler(id, inboundChannel))
                .option(ChannelOption.AUTO_READ, false);

        ChannelFuture f = b.connect("localhost", 8000);
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
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
//       if (outboundChannel.isActive()) {
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

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (remoteChannel != null) {
			/*
			 * Keeps the TCP connection alive. If you do not need that feature
			 * please uncomment the following commented line of code.
			 */
            // closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
