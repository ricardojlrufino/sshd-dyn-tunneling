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

package org.wso2.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;

public class ProxyBackendHandler extends ChannelInboundHandlerAdapter {
	private final Channel clientChannel;

	public ProxyBackendHandler(Channel clientChannel) {
		this.clientChannel = clientChannel;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		ctx.read();
		ctx.write(Unpooled.EMPTY_BUFFER);
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) {
		// Sends the response from the backend service to the client.
        clientChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
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
		ProxyFrontendHandler.closeOnFlush(clientChannel);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ProxyFrontendHandler.closeOnFlush(ctx.channel());
	}

}
