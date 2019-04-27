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
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;

/**
 * Creates a newly configured {@link io.netty.channel.ChannelPipeline} for a new
 * channel.
 */
public class SecureProxyInitializer extends ChannelInitializer<SocketChannel> {

	private Channel inbound;

	private final boolean isSecureBackend;
	private final String trustStoreLocation;
	private final String trustStorePassword;

	private static final Logger LOGGER = LoggerFactory.getLogger(SecureProxyInitializer.class);

	public SecureProxyInitializer(Channel inbound, boolean isSecureBackend,
	                              String trustStoreLocation, String trustStorePassword) {
		this.inbound = inbound;
		this.isSecureBackend = isSecureBackend;
		this.trustStoreLocation = trustStoreLocation;
		this.trustStorePassword = trustStorePassword;
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

		if (isSecureBackend) {
			LOGGER.info("Adding the SSL Handler to the pipeline");

			SSLEngine engine =
			                   SSLUtil.createClientSSLContext(trustStoreLocation,
			                                                  trustStorePassword).createSSLEngine();
			engine.setUseClientMode(true);

			pipeline.addLast("ssl", new SslHandler(engine));
		}

		pipeline.addLast(new HexDumpProxyBackendHandler(inbound));
	}
}
