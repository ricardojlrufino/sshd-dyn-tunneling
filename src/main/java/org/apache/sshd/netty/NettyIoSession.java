/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sshd.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.util.Attribute;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.DefaultCloseFuture;
import org.apache.sshd.common.io.*;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.closeable.AbstractCloseable;
import sshd.HttpRequestExtractHandler;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * The Netty based IoSession implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class NettyIoSession extends AbstractCloseable implements IoSession {
    protected final Map<Object, Object> attributes = new HashMap<>();
    protected final NettyIoService service;
    protected final IoHandler handler;
    protected final DefaultCloseFuture closeFuture = new DefaultCloseFuture(toString(), lock);
    protected final long id;
    protected ChannelHandlerContext context;
    protected SocketAddress remoteAddr;
    protected ChannelFuture prev;
    protected final ChannelInboundHandlerAdapter adapter = new Adapter();

    private final SocketAddress acceptanceAddress;

    public NettyIoSession(NettyIoService service, IoHandler handler, SocketAddress acceptanceAddress) {
        this.service = service;
        this.handler = handler;
        this.id = service.sessionSeq.incrementAndGet();
        this.acceptanceAddress = acceptanceAddress;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public Object getAttribute(Object key) {
        synchronized (attributes) {
            return attributes.get(key);
        }
    }

    @Override
    public Object setAttribute(Object key, Object value) {
        synchronized (attributes) {
            return attributes.put(key, value);
        }
    }

    @Override
    public Object setAttributeIfAbsent(Object key, Object value) {
        synchronized (attributes) {
            return attributes.putIfAbsent(key, value);
        }
    }

    @Override
    public Object removeAttribute(Object key) {
        synchronized (attributes) {
            return attributes.remove(key);
        }
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddr;
    }

    @Override
    public SocketAddress getLocalAddress() {
        Channel channel = context.channel();
        return channel.localAddress();
    }

    @Override
    public SocketAddress getAcceptanceAddress() {
        return acceptanceAddress;
    }

    @Override
    public IoWriteFuture writePacket(Buffer buffer) {
        int bufLen = buffer.available();
        ByteBuf buf = Unpooled.buffer(bufLen);
        buf.writeBytes(buffer.array(), buffer.rpos(), bufLen);
        DefaultIoWriteFuture msg = new DefaultIoWriteFuture(getRemoteAddress(), null);
        ChannelPromise next = context.newPromise();
        prev.addListener(whatever -> {
            if (context != null) {
                context.writeAndFlush(buf, next);
            }
        });
        prev = next;
        next.addListener(fut -> {
            if (fut.isSuccess()) {
                msg.setValue(Boolean.TRUE);
            } else {
                msg.setValue(fut.cause());
            }
        });
        return msg;
    }

    @Override
    public IoService getService() {
        return service;
    }

    @Override
    protected CloseFuture doCloseGracefully() {
        context.writeAndFlush(Unpooled.EMPTY_BUFFER)
            .addListener(ChannelFutureListener.CLOSE)
            .addListener(fut -> closeFuture.setClosed());
        return closeFuture;
    }

    @Override
    protected void doCloseImmediately() {
        context.close();
        super.doCloseImmediately();
    }

    protected void channelActive(ChannelHandlerContext ctx) throws Exception {
        context = ctx;
        Channel channel = ctx.channel();
        service.channelGroup.add(channel);
        service.sessions.put(id, NettyIoSession.this);
        prev = context.newPromise().setSuccess();
        remoteAddr = channel.remoteAddress();
        handler.sessionCreated(NettyIoSession.this);

        Attribute<IoConnectFuture> connectFuture =
            channel.attr(NettyIoService.CONNECT_FUTURE_KEY);
        IoConnectFuture future = connectFuture.get();
        if (future != null) {
            future.setSession(NettyIoSession.this);
        }
    }

    protected void channelInactive(ChannelHandlerContext ctx) throws Exception {
        service.sessions.remove(id);
        handler.sessionClosed(NettyIoSession.this);
        context = null;
    }

    protected void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        try {
            handler.messageReceived(NettyIoSession.this, NettySupport.asReadable(buf));
        } finally {
            buf.release();
        }
    }

    protected void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        handler.exceptionCaught(NettyIoSession.this, cause);
    }

    protected static class DefaultIoWriteFuture extends AbstractIoWriteFuture {
        public DefaultIoWriteFuture(Object id, Object lock) {
            super(id, lock);
        }
    }

    /**
     * Simple netty adapter to use as a bridge.
     */
    protected class Adapter extends ChannelInboundHandlerAdapter {
        public Adapter() {
            super();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            NettyIoSession.this.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            NettyIoSession.this.channelInactive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

            if(msg instanceof  ByteBuf) {
                HttpRequestExtractHandler conversor = new HttpRequestExtractHandler();
                HttpRequest request = conversor.decode(Unpooled.copiedBuffer((ByteBuf) msg));
                if (request != null) {

                    // ctx.channel().attr(HttpRequestExtractHandler.ATTR_HOST).set(request.headers().get(HttpHeaderNames.HOST));

                    NettyIoSession.this.setAttribute(HttpRequestExtractHandler.ATTR_HOST, request.headers().get(HttpHeaderNames.HOST));

                    System.err.println("Rquest : "+ request);
                    request.headers().getAll(HttpHeaderNames.COOKIE).forEach(h -> {
                        ServerCookieDecoder.STRICT.decode(h).forEach(c -> {
                            System.out.println("Cookie :" + c);
                        });
                    });
                }
            }

            NettyIoSession.this.channelRead(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            NettyIoSession.this.exceptionCaught(ctx, cause);
        }
    }
}
