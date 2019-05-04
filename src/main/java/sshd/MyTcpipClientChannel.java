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

import org.apache.sshd.client.future.DefaultOpenFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.channel.Window;
import org.apache.sshd.common.forward.TcpipClientChannel;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.net.SshdSocketAddress;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * TODO: Add docs.
 *
 * @author Ricardo JL Rufino
 *         Date: 04/05/19
 */
public class MyTcpipClientChannel extends TcpipClientChannel {
    public MyTcpipClientChannel(Type type, IoSession serverSession, SshdSocketAddress remote) {
        super(type, serverSession, remote);
    }

    private SshdSocketAddress tunnelEntrance;
    private SshdSocketAddress tunnelExit;

    @Override
    public synchronized OpenFuture open() throws IOException {
        InetSocketAddress src;
        SshdSocketAddress dst;
        InetSocketAddress loc = (InetSocketAddress) serverSession.getLocalAddress();
        Type openType = getTcpipChannelType();
        switch (openType) {
            case Direct:
                src = (InetSocketAddress) serverSession.getRemoteAddress();
                dst = this.remote;
                tunnelEntrance = new SshdSocketAddress(loc.getHostString(), loc.getPort());
                tunnelExit = new SshdSocketAddress(dst.getHostName(), dst.getPort());
                break;
            case Forwarded:
                src = (InetSocketAddress) serverSession.getRemoteAddress();
                dst = localEntry;
                tunnelEntrance = new SshdSocketAddress(src.getHostString(), src.getPort());
                tunnelExit = new SshdSocketAddress(loc.getHostString(), loc.getPort());
                break;
            default:
                throw new SshException("Unknown client channel type: " + openType);
        }

        if (closeFuture.isClosed()) {
            throw new SshException("Session has been closed");
        }

        // make sure the pending messages queue is 1st in line
        openFuture = new DefaultOpenFuture(src, lock)
                .addListener(getPendingMessagesQueue());
        if (log.isDebugEnabled()) {
            log.debug("open({}) send SSH_MSG_CHANNEL_OPEN", this);
        }

        Session session = getSession();
        String srcHost = src.getHostString();
        String dstHost = dst.getHostName();
        Window wLocal = getLocalWindow();
        String type = getChannelType();
        Buffer buffer = session.createBuffer(SshConstants.SSH_MSG_CHANNEL_OPEN,
                type.length() + srcHost.length() + dstHost.length() + Long.SIZE);
        buffer.putString(type);
        buffer.putInt(getId());
        buffer.putInt(wLocal.getSize());
        buffer.putInt(wLocal.getPacketSize());
//        buffer.putString("http1");
        buffer.putString(dstHost);
//        buffer.putInt(9000);
//        buffer.putString(srcHost);
//        buffer.putInt(9000); // CHANGED !

        buffer.putInt(dst.getPort());
        buffer.putString(srcHost);
        buffer.putInt(src.getPort());

        writePacket(buffer);
        return openFuture;
    }

    @Override
    public SshdSocketAddress getTunnelEntrance() {
        return tunnelEntrance;
    }

    @Override
    public SshdSocketAddress getTunnelExit() {
        return tunnelExit;
    }
}
