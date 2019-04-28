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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

public class HexDumpProxyInitializer extends ChannelInitializer<SocketChannel> {
	private final String remoteHost;
	private final int remotePort;

	public HexDumpProxyInitializer(String remoteHost, int remotePort) {
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
	}

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();

		pipeline.addLast(
//				new LoggingHandler(LogLevel.INFO),
//                new HttpResponseEncoder(),
//                new HttpRequestDecoder(),
//                new HttpObjectAggregator(4028),
//                new ChunkedWriteHandler(),
//                new HttpSnoopClientHandler2(),
//                new HttpSnoopClientHandler(),
				new HexDumpProxyFrontendHandler(remoteHost, remotePort)
		);

	}
}
