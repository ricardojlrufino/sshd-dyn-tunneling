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

import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpRequest;

public class HexDumpProxyBackendHandler extends ChannelInboundHandlerAdapter {
	private final Channel inboundChannel;

	public HexDumpProxyBackendHandler(Channel inboundChannel) {
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

		if(msg instanceof HttpRequest){
			HttpRequest request = (HttpRequest) msg;
			ByteBufHolder holder  = (ByteBufHolder) request;
			System.out.println("Request Bacend !!! >>>>>>>> "  + holder.content());
//			try {
//				HttpRequestEncoder encoder = new HttpRequestEncoder();
//
//                inboundChannel.
//
//                ChannelPromise channelPromise = new DefaultChannelPromise(inboundChannel);
//                encoder.write(ctx, request, channelPromise);
//
//            } catch (HttpPostRequestEncoder.ErrorDataEncoderException e) {
//				e.printStackTrace();
//			} catch (Exception e) {
//                e.printStackTrace();
//
            StringBuffer sb = new StringBuffer();

            sb.append("GET / HTTP/1.1").append("\r\n");
            sb.append("User-Agent: Mozilla/4.0 (compatible; MSIE5.01; Windows NT)").append("\r\n");
            sb.append("Host: localhost").append("\r\n");
            sb.append("Accept-Language: en-us").append("\r\n");
            sb.append("Accept-Encoding: gzip, deflate").append("\r\n");
            sb.append("Connection: Keep-Alive").append("\r\n");

            inboundChannel.writeAndFlush(sb.toString()).addListener(new ChannelFutureListener() {
				public void operationComplete(ChannelFuture future) {
					if (future.isSuccess()) {
						ctx.channel().read();
					} else {
						future.channel().close();
					}
				}
			});

		}else{
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

	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		HexDumpProxyFrontendHandler.closeOnFlush(inboundChannel);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		HexDumpProxyFrontendHandler.closeOnFlush(ctx.channel());
	}

}
