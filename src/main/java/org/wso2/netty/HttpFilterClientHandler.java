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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;

import java.util.concurrent.atomic.AtomicBoolean;

public class HttpFilterClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;

            System.out.println("STATUS: " + request.uri());
            System.out.println("VERSION: " + request.protocolVersion());
            System.out.println();

//            request.headers().set("Host", "homologacao2.edu3.com.br");

            HttpHeaders headers = request.headers();

            String originalHost = headers.get("X-Forwarded-Host");
            String deviceName = originalHost.substring(0, originalHost.indexOf("."));
            System.out.println("DeviceName: " + deviceName);


            AtomicBoolean hasCurrentDevice = new AtomicBoolean(false);

            headers.getAll(HttpHeaderNames.COOKIE).forEach(h -> {
                ServerCookieDecoder.STRICT.decode(h).forEach(c -> {
                    if(c.name().equals("odev.proxy.device")){
                        hasCurrentDevice.set(true);
                    }
                    System.out.println("Cookie :" + c);
                });
            });


            // Check if have select device.
            if(!hasCurrentDevice.get()){
                System.out.println("NAO TEM O COOKE !!!!");
                ByteBuf buffer = Unpooled.wrappedBuffer(Unpooled.EMPTY_BUFFER);
                HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TEMPORARY_REDIRECT, buffer);
//                                            HttpHeaders.setContentLength(response, buffer.readableBytes());
                HttpHeaders.setHeader(response, HttpHeaderNames.LOCATION, "http://www.google.com");

                response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(new DefaultCookie("odev.proxy.device", "OK")));

                ctx.fireChannelRead(response);
                return;

            }

            if (!headers.isEmpty()) {
                for (String name : headers.names()) {
                    for (String value : headers.getAll(name)) {
                        System.out.println("HEADER: " + name + " = " + value);
                    }
                }
                System.out.println();
            }

            if (HttpHeaders.isTransferEncodingChunked(request)) {
                System.out.println("CHUNKED CONTENT {");
            } else {
                System.out.println("CONTENT {");
            }
        }
        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;

//            System.out.print(content.content().toString(CharsetUtil.UTF_8));
//            System.out.flush();

            if (content instanceof LastHttpContent) {
                System.out.println("} END OF CONTENT");
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


}