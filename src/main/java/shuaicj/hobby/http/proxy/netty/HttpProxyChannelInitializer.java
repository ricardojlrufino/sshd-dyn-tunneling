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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The channel initializer.
 *
 * @author shuaicj 2017/09/21
 */
public class HttpProxyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final AtomicLong taskCounter = new AtomicLong();

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {

        ChannelPipeline channelPipeline = ch.pipeline();

//        channelPipeline.addLast("encoder", new HttpResponseEncoder());
//        channelPipeline.addLast("decoder", new HttpRequestDecoder());
//        channelPipeline.addLast("aggregator", new HttpObjectAggregator(4028));
//        channelPipeline.addLast("chunkedWriter", new ChunkedWriteHandler());

        channelPipeline.addLast(
//                new LoggingHandler(LogLevel.DEBUG),
//                new HttpSnoopClientHandler(),
//                new HttpSnoopClientHandler2(),
                new HttpProxyClientHandler("task-" + taskCounter.getAndIncrement())
        );
    }
}
