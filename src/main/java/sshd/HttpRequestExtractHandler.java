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

package sshd;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;

/**
 * TODO: Add docs.
 *
 * @author Ricardo JL Rufino
 *         Date: 04/05/19
 */
public class HttpRequestExtractHandler extends ChannelInboundHandlerAdapter {

//    public static final AttributeKey<String> ATTR_HOST = AttributeKey.newInstance("REQUEST-HOST");
    public static final String ATTR_HOST = "REQUEST-HOST";

    private HttpRequest request;

    public HttpRequest getRequest() {
        return request;
    }

    public HttpRequest decode(Object msg){
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpRequestDecoder(), this);
        embeddedChannel.writeInbound(Unpooled.copiedBuffer((ByteBuf) msg));
        embeddedChannel.close();
        return request;
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if(msg instanceof HttpRequest) {
            request = (HttpRequest) msg;
        }
    }

}
