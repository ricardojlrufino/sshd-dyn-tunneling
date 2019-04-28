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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import shuaicj.hobby.http.proxy.netty.HttpSnoopClientHandler2;

public class HexDumpProxyFrontendHandler extends ChannelInboundHandlerAdapter {
	private final String remoteHost;
	private final int remotePort;

	private volatile Channel outboundChannel;

	public HexDumpProxyFrontendHandler(String remoteHost, int remotePort) {
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		final Channel inboundChannel = ctx.channel();

		// Start the connection attempt.
		Bootstrap b = new Bootstrap();
		// b.group(inboundChannel.eventLoop()).channel(ctx.channel().getClass())
		b.group(new NioEventLoopGroup())
		 .channel(NioSocketChannel.class)
		 .handler(new BackendProxyInitializer(inboundChannel))
		 .option(ChannelOption.AUTO_READ, false);
		ChannelFuture f = b.connect(remoteHost, remotePort);
		outboundChannel = f.channel();

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
		if (outboundChannel.isActive()) {
			/*
			 * Sends the client request to the backend service.
			 */

            HttpSnoopClientHandler2 clientHandler2 = new HttpSnoopClientHandler2();
            EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpRequestDecoder(),  clientHandler2);
            embeddedChannel.writeInbound(Unpooled.copiedBuffer((ByteBuf) msg));
            embeddedChannel.close();

            HttpRequest request = clientHandler2.getRequest();

            if(request != null){
                request.headers().getAll(HttpHeaderNames.COOKIE).forEach(h -> {
                    ServerCookieDecoder.STRICT.decode(h).forEach(c -> {
                        System.out.println("Cookie :" + c);
                    });
                });

                HttpHeaders headers = request.headers();
                if (!headers.isEmpty()) {
                    for (String name : headers.names()) {
                        for (String value : headers.getAll(name)) {
                            System.out.println("HEADER: " + name + " = " + value);
                        }
                    }
                    System.out.println();
                }

                System.out.println("FINAL: " + request.uri());
                System.out.println("=====================");
            }else{
                System.out.println("REQUEST NOT DECODED !!");
            }



            outboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
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
		if (outboundChannel != null) {
			/*
			 * Keeps the TCP connection alive. If you do not need that feature
			 * please uncomment the following commented line of code.
			 */
			 closeOnFlush(outboundChannel);
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
