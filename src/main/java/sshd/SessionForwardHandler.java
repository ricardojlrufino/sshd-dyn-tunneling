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

import org.apache.sshd.client.channel.ClientChannelPendingMessagesQueue;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.common.forward.LocalForwardingEntry;
import org.apache.sshd.common.forward.TcpipClientChannel;
import org.apache.sshd.common.forward.TcpipForwardingExceptionMarker;
import org.apache.sshd.common.io.IoHandler;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.Readable;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * TODO: Add docs.
 *
 * @author Ricardo JL Rufino
 *         Date: 04/05/19
 */
public class SessionForwardHandler implements IoHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private Map<String, ConnectionService> connections = new HashMap<>();

    private final Set<LocalForwardingEntry> localForwards = new HashSet<>();

    private final AtomicLong messagesCounter = new AtomicLong(0L);
    private final boolean debugEnabled = log.isDebugEnabled();
    private final boolean traceEnabled = log.isTraceEnabled();

    SessionForwardHandler() {
        super();
    }

    @Override
    public void sessionCreated(IoSession session) throws Exception {

    }

    @Override
    public void sessionClosed(IoSession session) throws Exception {
        TcpipClientChannel channel = (TcpipClientChannel) session.removeAttribute(TcpipClientChannel.class);
        Throwable cause = (Throwable) session.removeAttribute(TcpipForwardingExceptionMarker.class);
        if (debugEnabled) {
            log.debug("sessionClosed({}) closing channel={} after {} messages - cause={}",
                    session, channel, messagesCounter, (cause == null) ? null : cause.getClass().getSimpleName());
        }
        if (channel == null) {
            return;
        }

        if (cause != null) {
            // If exception occurred close the channel immediately
            channel.close(true);
        } else {
                /*
                 *  Make sure channel is pending messages have all been sent in case the client was very fast
                 *  and sent data + closed the connection before channel open was completed.
                 */
            OpenFuture openFuture = channel.getOpenFuture();
            Throwable err = openFuture.getException();
            ClientChannelPendingMessagesQueue queue = channel.getPendingMessagesQueue();
            OpenFuture completedFuture = queue.getCompletedFuture();
            if (err == null) {
                err = completedFuture.getException();
            }
            boolean immediately = err != null;
            if (immediately) {
                channel.close(true);
            } else {
                completedFuture.addListener(f -> {
                    Throwable thrown = f.getException();
                    channel.close(immediately || (thrown != null));
                });
            }
        }
    }

    @Override
    public void messageReceived(IoSession session, Readable message) throws Exception {

        // check if if connected
        TcpipClientChannel channel = (TcpipClientChannel) session.getAttribute(TcpipClientChannel.class);

        // Need connect
        if(channel == null){

            Object host = session.getAttribute(HttpRequestExtractHandler.ATTR_HOST);

            ConnectionService service = getConnectionService(host.toString());

            Session sshSession = service.getSession();

            System.err.println(" messageReceived >>> host: " + host + ", session: "+ sshSession + ", isOpen: "+ sshSession.isOpen());

            InetSocketAddress local = (InetSocketAddress) session.getLocalAddress();
            int localPort = local.getPort();

//                SshdSocketAddress remote = localForwards.stream().filter(p -> p.getAlias().equals(host))
//                        .findAny()
//                        .orElse(null);

//                SshdSocketAddress remote = localToRemote.get(localPort);
            SshdSocketAddress remote = null;
//                System.err.println(" foud remote: "+ remote);

            TcpipClientChannel.Type channelType = (remote == null)
                    ? TcpipClientChannel.Type.Forwarded
                    : TcpipClientChannel.Type.Direct;

            channel = new MyTcpipClientChannel(channelType, session, remote);
            session.setAttribute(TcpipClientChannel.class, channel);

            // Propagate original requested host name - see SSHD-792
            if (channelType == TcpipClientChannel.Type.Forwarded) {
                SocketAddress accepted = session.getAcceptanceAddress();
                LocalForwardingEntry localEntry = null;
                if (accepted instanceof InetSocketAddress) {
                    synchronized (localForwards) {
                        for (LocalForwardingEntry address : localForwards) {
                            if(address.getHostName().equals(host)){
                                localEntry = address;
                                System.out.println("FOUND >>>>" + localEntry);
                            }
                        }
                    }
                }

                if (localEntry != null) {
                    if (debugEnabled) {
                        log.debug("sessionCreated({})[local={}, remote={}, accepted={}] localEntry={}",
                                session, local, remote, accepted, localEntry);
                    }
                    channel.updateLocalForwardingEntry(localEntry);
                } else {
                    log.warn("sessionCreated({})[local={}, remote={}] cannot locate original local entry for accepted={}",
                            session, local, remote, accepted);
                }
            } else {
                if (debugEnabled) {
                    log.debug("sessionCreated({}) local={}, remote={}", session, local, remote);
                }
            }

            service.registerChannel(channel);
            TcpipClientChannel finalChannel = channel;

            long start = System.currentTimeMillis();
            channel.open().addListener(future -> {
                Throwable t = future.getException();
                if (t != null) {
                    log.warn("Failed ({}) to open channel for session={}: {}",
                            t.getClass().getSimpleName(), session, t.getMessage());
                    if (debugEnabled) {
                        log.debug("sessionCreated(" + session + ") channel=" + finalChannel + " open failure details", t);
                    }
                    service.unregisterChannel(finalChannel);
                    finalChannel.close(false);

                }else{ // send after connect

                }
            }).await(5000);

            System.err.println(" >>>> time : " +  (System.currentTimeMillis() - start) + ", isOPen: "+ sshSession.isOpen());
        }

        sendMessage(session, message);

    }

    private void sendMessage(IoSession session, Readable message) throws IOException {

        TcpipClientChannel channel = (TcpipClientChannel) session.getAttribute(TcpipClientChannel.class);
        long totalMessages = messagesCounter.incrementAndGet();
        Buffer buffer = new ByteArrayBuffer(message.available() + Long.SIZE, false);
        buffer.putBuffer(message);

        if (traceEnabled) {
            log.trace("messageReceived({}) channel={}, count={}, handle len={}",
                    session, channel, totalMessages, message.available());
        }

        OpenFuture future = channel.getOpenFuture();
        Consumer<Throwable> errHandler = future.isOpened() ? null : e -> {
            try {
                exceptionCaught(session, e);
            } catch (Exception err) {
                log.warn("messageReceived({}) failed ({}) to signal {}[{}] on channel={}: {}",
                        session, err.getClass().getSimpleName(), e.getClass().getSimpleName(),
                        e.getMessage(), channel, err.getMessage());
            }
        };
        ClientChannelPendingMessagesQueue messagesQueue = channel.getPendingMessagesQueue();
        int pendCount = messagesQueue.handleIncomingMessage(buffer, errHandler);
        if (traceEnabled) {
            log.trace("messageReceived({}) channel={} pend count={} after processing message",
                    session, channel, pendCount);
        }
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        session.setAttribute(TcpipForwardingExceptionMarker.class, cause);
        if (debugEnabled) {
            log.debug("exceptionCaught({}) {}: {}", session, cause.getClass().getSimpleName(), cause.getMessage());
        }
        if (traceEnabled) {
            log.trace("exceptionCaught(" + session + ") caught exception details", cause);
        }
        session.close(true);
    }

    public void register(ConnectionService connectionService, String hostName){
        connections.put(hostName, connectionService);
    }

    public void unregister(ConnectionService connectionService){

        Set<String> keys = connections.keySet();

        for (String key : keys) {
            if(connectionService == connections.get(key)){
                connections.remove(key);
            }
        }
    }

    public ConnectionService getConnectionService(String host){
        return connections.get(host);
    }

    public Set<LocalForwardingEntry> getLocalForwards() {
        return localForwards;
    }
}
