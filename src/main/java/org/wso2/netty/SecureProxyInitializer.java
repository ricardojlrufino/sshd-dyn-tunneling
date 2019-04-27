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
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a newly configured {@link io.netty.channel.ChannelPipeline} for a new
 * channel.
 */
public class SecureProxyInitializer extends ChannelInitializer<SocketChannel> {

	private Channel inbound;
	private static final Logger LOGGER = LoggerFactory.getLogger(SecureProxyInitializer.class);

	public SecureProxyInitializer(Channel inbound) {
		this.inbound = inbound;
	}

	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();

		// Add SSL handler first to encrypt and decrypt everything.
		// In this example, we use a bogus certificate in the server side
		// and accept any invalid certificates in the client side.
		// You will need something more complicated to identify both
		// and server in the real world.

		pipeline.addLast(new LoggingHandler(LogLevel.DEBUG));

		pipeline.addLast(new HexDumpProxyBackendHandler(inbound));
	}
}
