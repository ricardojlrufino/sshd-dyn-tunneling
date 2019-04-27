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

import io.netty.buffer.Unpooled;
import io.netty.channel.*;

/**
 * Handle data from remote.
 *
 * @author shuaicj 2017/09/21
 */
public class HttpProxyRemoteHandler extends ChannelInboundHandlerAdapter {

    private final Channel inboundChannel;

    public HttpProxyRemoteHandler(String id, Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.read();
        ctx.write(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        // Sends the response from the backend service to the client.
        inboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    future.channel().close();
                }
            }
        });
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        flushAndClose(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
//        logger.error(id + " shit happens", e);
        e.printStackTrace();
        flushAndClose(inboundChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }




}
