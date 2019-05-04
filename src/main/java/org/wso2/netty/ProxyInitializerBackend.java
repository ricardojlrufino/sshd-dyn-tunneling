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

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a newly configured {@link io.netty.channel.ChannelPipeline} for a new
 * channel.
 */
public class ProxyInitializerBackend extends ChannelInitializer<SocketChannel> {

	private Channel clientChannel;
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyInitializerBackend.class);

	public ProxyInitializerBackend(Channel clientChannel) {
		this.clientChannel = clientChannel;
	}

	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("encoder", new HttpRequestEncoder());
        pipeline.addLast("decoder", new HttpResponseDecoder());
        pipeline.addLast("idle", new IdleStateHandler(0, 0, Constants.IDLE_TIMEOUT));
		pipeline.addLast(new ProxyBackendHandler(clientChannel));
	}
}
