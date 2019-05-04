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
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.timeout.IdleStateHandler;

public class ProxyInitializerFrontend extends ChannelInitializer<SocketChannel> {
    private final String remoteHost;
    private final int remotePort;

    public ProxyInitializerFrontend(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("idle", new IdleStateHandler(0, 0, Constants.IDLE_TIMEOUT));

        pipeline.addLast("filter", new HttpFilterClientHandler());
        pipeline.addLast("handler", new ProxyFrontendHandler(remoteHost, remotePort));

    }
}
