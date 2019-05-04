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

import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.channel.throttle.ChannelStreamPacketWriterResolver;
import org.apache.sshd.common.forward.ForwardingFilter;
import org.apache.sshd.common.forward.ForwardingFilterFactory;
import org.apache.sshd.common.forward.PortForwardingEventListener;
import org.apache.sshd.common.kex.KexProposalOption;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.ReservedSessionMessagesHandler;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.StaticPasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.AcceptAllPublickeyAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.forward.TcpForwardingFilter;
import org.apache.sshd.server.shell.ProcessShellFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * TODO: Add docs.
 *
 * @author Ricardo JL Rufino
 *         Date: 04/05/19
 */
public class SSHDTest {

    public static void main(String[] args) {
        SshServer sshd = SshServer.setUpDefaultServer();

//        KeyPairProvider pairProvider = new SimpleGeneratorHostKeyProvider();
        KeyPairProvider pairProvider = new FileKeyPairProvider(Paths.get("/media/ricardo/Dados/TEMP/ssh/ssh_host_rsa_key"));
        sshd.setKeyPairProvider(pairProvider);

//        sshd.setKeyboardInteractiveAuthenticator(new DefaultKeyboardInteractiveAuthenticator());
        sshd.setPasswordAuthenticator(new StaticPasswordAuthenticator(true));
        sshd.setForwardingFilter(new AcceptAllForwardingFilter());
        // sshd.setShellFactory();
        sshd.setPublickeyAuthenticator(AcceptAllPublickeyAuthenticator.INSTANCE);


        List<NamedFactory<Channel>> channelFactories = sshd.getChannelFactories();

        ChannelStreamPacketWriterResolver channelStreamPacketWriterResolver = sshd.getChannelStreamPacketWriterResolver();

        PortForwardingEventListener portForwardingEventListenerProxy = sshd.getPortForwardingEventListenerProxy();

        ReservedSessionMessagesHandler reservedSessionMessagesHandler = sshd.getReservedSessionMessagesHandler();

        ForwardingFilterFactory forwarderFactory = sshd.getForwarderFactory();

        TcpForwardingFilter tcpForwardingFilter = sshd.getTcpForwardingFilter();



        sshd.setShellFactory(new ProcessShellFactory(new String[] { "/bin/sh", "-i", "-l" }));

        sshd.setPort(4440);

        sshd.setForwarderFactory(new ForwardingFilterFactory() {
            MyDefaultForwardingFilter myDefaultForwardingFilter;
            @Override
            public ForwardingFilter create(ConnectionService service) {
                if(myDefaultForwardingFilter == null) myDefaultForwardingFilter = new MyDefaultForwardingFilter(service);
                return myDefaultForwardingFilter;
            }
        });

        sshd.addPortForwardingEventListener(new PortForwardingEventListener() {
            @Override
            public void establishedDynamicTunnel(Session session, SshdSocketAddress local, SshdSocketAddress boundAddress, Throwable reason) throws IOException {
                System.err.println("establishedDynamicTunnel : " + boundAddress);
            }
        });

        sshd.addChannelListener(new ChannelListener() {
            @Override
            public void channelClosed(Channel channel, Throwable reason) {
                System.err.println("channelClosed: " + channel);
            }

            @Override
            public void channelOpenSuccess(Channel channel) {
                System.err.println("channelOpenSuccess: " + channel);
            }
        });

        sshd.addSessionListener(new SessionListener() {
            @Override
            public void sessionClosed(Session session) {
                System.err.println("sessionClosed: " + session);
            }

            @Override
            public void sessionCreated(Session session) {
                System.err.println("sessionCreated: " + session);
            }

            @Override
            public void sessionNegotiationEnd(Session session, Map<KexProposalOption, String> clientProposal, Map<KexProposalOption, String> serverProposal, Map<KexProposalOption, String> negotiatedOptions, Throwable reason) {
                System.err.println("sessionNegotiationEnd : " + clientProposal);
            }
        });



        try {
            sshd.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Server started ?");

        List<AbstractSession> activeSessions = sshd.getActiveSessions();

        // Set up ...
        try {
            Object lock = new Object();
            synchronized (lock) {
                while (true) {
                    lock.wait();
                }
            }
        } catch (InterruptedException ex) {
        }
    }
}
