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
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle data from client.
 *
 * @author shuaicj 2017/09/21
 */
public class HttpProxyClientHandlerBck extends ChannelInboundHandlerAdapter {

    private final String id;
    private Channel clientChannel;
    private Channel remoteChannel;

//    private HttpProxyClientHeader header;
    private Logger logger = LoggerFactory.getLogger(HttpProxyClientHandlerBck.class);

    public HttpProxyClientHandlerBck(String id) {
        this.id = id;
//        this.header = new HttpProxyClientHeader();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        clientChannel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
//        if (header.isComplete()) {
//            remoteChannel.writeAndFlush(msg); // just forward
//            return;
//        }

//        ByteBuf in = (ByteBuf) msg;
//        header.digest(in);

//        if (!header.isComplete()) {
//            in.release();
//            return;
//        }

//        logger.info(id + " {}", header);
        clientChannel.config().setAutoRead(false); // disable AutoRead until remote connection is ready

//        if (header.isHttps()) { // if https, respond 200 to create tunnel
//            clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes()));
//        }

        Bootstrap b = new Bootstrap();
        b.group(clientChannel.eventLoop()) // use the same EventLoop
                .channel(clientChannel.getClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(
//                                new HttpSnoopClientHandler2(),
                                new HttpProxyRemoteHandler(id, clientChannel)
                        );
                    }
                });
//                .handler(new HttpProxyRemoteHandler(id, clientChannel));

        ChannelFuture f = b.connect("191.17.14.7", 8000);
        remoteChannel = f.channel();

        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                clientChannel.config().setAutoRead(true); // connection is ready, enable AutoRead
//                if (!header.isHttps()) { // forward header and remaining bytes
//                    remoteChannel.write(header.getByteBuf());
//                }
                remoteChannel.writeAndFlush(msg);
            } else {
//                msg.release();
                clientChannel.close();
            }
        });
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
