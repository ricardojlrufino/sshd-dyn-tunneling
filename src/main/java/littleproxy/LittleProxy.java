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

package littleproxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TODO: Add docs.
 *
 * @author Ricardo JL Rufino
 *         Date: 28/04/19
 */
public class LittleProxy {

    public static void main(String[] args) {
        HttpProxyServer server =
                DefaultHttpProxyServer.bootstrap()
                        .withPort(9292)
                        .withAllowRequestToOriginServer(true)
                        .withFiltersSource(new HttpFiltersSourceAdapter() {
                            public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
                                return new HttpFiltersAdapter(originalRequest) {
                                    @Override
                                    public HttpResponse clientToProxyRequest(HttpObject httpObject) {
                                        // TODO: implement your filtering here
                                        if(httpObject instanceof  HttpRequest){
                                            HttpRequest request = (HttpRequest) httpObject;
                                            request.headers().set("Host", "localhost:8001");

                                            // Check selected device in cookie

                                            AtomicBoolean hasCurrentDevice = new AtomicBoolean(false);

                                            request.headers().getAll(HttpHeaderNames.COOKIE).forEach(h -> {
                                                ServerCookieDecoder.STRICT.decode(h).forEach(c -> {
                                                    if(c.name().equals("odev.proxy.device")){
                                                        hasCurrentDevice.set(false);
                                                    }
                                                    System.out.println("Cookie :" + c);
                                                });
                                            });


//                                            // Check if have select device.
//                                            if(!hasCurrentDevice.get()){
//                                                ByteBuf buffer = Unpooled.wrappedBuffer(Unpooled.EMPTY_BUFFER);
//                                                HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TEMPORARY_REDIRECT, buffer);
////                                            HttpHeaders.setContentLength(response, buffer.readableBytes());
//                                                HttpHeaders.setHeader(response, HttpHeaderNames.LOCATION, "http://www.google.com");
//                                                return response;
//
//                                            }

                                        }
                                        return null;
                                    }

                                    @Override
                                    public HttpObject serverToProxyResponse(HttpObject httpObject) {
                                        // TODO: implement your filtering here
                                        return httpObject;
                                    }

//                                    @Override
//                                    public InetSocketAddress proxyToServerResolutionStarted(String resolvingServerHostAndPort) {
//                                        InetAddress addr = null;
//                                        try {
//                                            addr = InetAddress.getByName("localhost");
//                                        } catch (UnknownHostException e) {
//                                            e.printStackTrace();
//                                        }
//                                        return new InetSocketAddress(addr, 8000);
//                                    }
                                };
                            }
                        })
                        .start();
    }
}
