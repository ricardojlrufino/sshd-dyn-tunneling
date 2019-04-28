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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;

public class HttpSnoopClientHandler2 extends ChannelInboundHandlerAdapter {

    private HttpRequest request;

    public HttpRequest getRequest() {
        return request;
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) {

//        HttpProxyClientHeader header = new HttpProxyClientHeader();
//        header.digest((ByteBuf) msg);

        if(msg instanceof ByteBuf) {
            String teste = formatByteBuf(ctx, "teste", (ByteBuf) msg);
            System.out.println("BYTEBUFFER >>> " + teste);
        }

        if (msg instanceof HttpRequest) {
            request = (HttpRequest) msg;

            System.out.println("URI: " + request.uri());
            System.out.println("VERSION: " + request.getProtocolVersion());
            System.out.println();

//            request.headers().add("Host", "homologacao2.edu3.com.br");

            if (!request.headers().isEmpty()) {
                for (String name : request.headers().names()) {
                    for (String value : request.headers().getAll(name)) {
                        System.out.println("HEADER: " + name + " = " + value);
                    }
                }
                System.out.println();
            }

            if (HttpHeaders.isTransferEncodingChunked(request)) {
                System.out.println("CHUNKED CONTENT !!!");
            }
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }



    private static String formatByteBuf(ChannelHandlerContext ctx, String eventName, ByteBuf msg) {
        String chStr = ctx.channel().toString();
        int length = msg.readableBytes();
        if (length == 0) {
            StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 4);
            buf.append(chStr).append(' ').append(eventName).append(": 0B");
            return buf.toString();
        } else {
            byte[] bytes = ByteBufUtil.getBytes(msg);
            return new String(bytes);
        }
    }

}