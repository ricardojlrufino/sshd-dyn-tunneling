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

import org.apache.sshd.common.forward.ForwardingFilter;
import org.apache.sshd.common.forward.ForwardingFilterFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.StaticPasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.AcceptAllPublickeyAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.shell.ProcessShellFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

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
        sshd.setPublickeyAuthenticator(AcceptAllPublickeyAuthenticator.INSTANCE);

        sshd.setShellFactory(new ProcessShellFactory(new String[] { "/bin/sh", "-i", "-l" }));
//        sshd.setShellFactory(new InteractiveProcessShellFactory());
//        sshd.setShellFactory(new ShellFactory() {
//            @Override
//            public Command create() {
//                return new MyCommand();
//            }
//        });


        sshd.setPort(4440);

//        sshd.setForwarderFactory(new ForwardingFilterFactory() {
//            MyDefaultForwardingFilter myDefaultForwardingFilter;
//            @Override
//            public ForwardingFilter create(ConnectionService service) {
//                if(myDefaultForwardingFilter == null) myDefaultForwardingFilter = new MyDefaultForwardingFilter(service);
//                return myDefaultForwardingFilter;
//            }
//        });


        SessionForwardHandler sessionForwardManager = new SessionForwardHandler();

        sshd.setForwarderFactory(new ForwardingFilterFactory() {
            @Override
            public ForwardingFilter create(ConnectionService service) {
                System.err.println("Create ForwardingFilter for: "+ service);
                return new MyDefaultForwardingFilter(service, sessionForwardManager);
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
