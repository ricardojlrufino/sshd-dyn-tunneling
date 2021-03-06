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

import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.common.*;
import org.apache.sshd.common.forward.*;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoHandler;
import org.apache.sshd.common.io.IoHandlerFactory;
import org.apache.sshd.common.io.IoServiceFactory;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionHolder;
import org.apache.sshd.common.util.EventListenerUtils;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.Invoker;
import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.closeable.AbstractInnerCloseable;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.forward.TcpForwardingFilter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 *
 * @author Ricardo JL Rufino
 *         Date: 04/05/19
 */
public class MyDefaultForwardingFilter extends AbstractInnerCloseable
        implements ForwardingFilter, SessionHolder<Session>, PortForwardingEventListenerManager {

    private static AtomicInteger portSequence = new AtomicInteger(1); // CHANGED: only for demo...

    /**
     * Used to configure the timeout (milliseconds) for receiving a response
     * for the forwarding request
     *
     * @see #DEFAULT_FORWARD_REQUEST_TIMEOUT
     */
    public static final String FORWARD_REQUEST_TIMEOUT = "tcpip-forward-request-timeout";

    /**
     * Default value for {@value #FORWARD_REQUEST_TIMEOUT} if none specified
     */
    public static final long DEFAULT_FORWARD_REQUEST_TIMEOUT = TimeUnit.SECONDS.toMillis(15L);

    public static final Set<ClientChannelEvent> STATIC_IO_MSG_RECEIVED_EVENTS =
            Collections.unmodifiableSet(EnumSet.of(ClientChannelEvent.OPENED, ClientChannelEvent.CLOSED));

    private final ConnectionService service;
    private final IoHandlerFactory socksProxyIoHandlerFactory = () -> new SocksProxy(getConnectionService());
    private final Session sessionInstance;
    private SessionForwardHandler sessionForwardHandler;

    private final Object localLock = new Object();
    private final Map<Integer, SshdSocketAddress> localToRemote = new TreeMap<>(Comparator.naturalOrder());
    private final Map<Integer, InetSocketAddress> boundLocals = new TreeMap<>(Comparator.naturalOrder());

    private final Object dynamicLock = new Object();
    private final Map<Integer, SshdSocketAddress> remoteToLocal = new TreeMap<>(Comparator.naturalOrder());
    private final Map<Integer, SocksProxy> dynamicLocal = new TreeMap<>(Comparator.naturalOrder());
    private final Map<Integer, InetSocketAddress> boundDynamic = new TreeMap<>(Comparator.naturalOrder());

    private final Collection<PortForwardingEventListener> listeners = new CopyOnWriteArraySet<>();
    private final Collection<PortForwardingEventListenerManager> managersHolder = new CopyOnWriteArraySet<>();
    private final PortForwardingEventListener listenerProxy;

    private static IoAcceptor acceptor;

    public MyDefaultForwardingFilter(ConnectionService service, SessionForwardHandler sessionForwardManager) {
        this.service = Objects.requireNonNull(service, "No connection service");
        this.sessionInstance = Objects.requireNonNull(service.getSession(), "No session");
        this.sessionForwardHandler = sessionForwardManager;
        this.listenerProxy = EventListenerUtils.proxyWrapper(PortForwardingEventListener.class, getClass().getClassLoader(), listeners);
    }

    @Override
    public PortForwardingEventListener getPortForwardingEventListenerProxy() {
        return listenerProxy;
    }

    @Override
    public void addPortForwardingEventListener(PortForwardingEventListener listener) {
        listeners.add(PortForwardingEventListener.validateListener(listener));
    }

    @Override
    public void removePortForwardingEventListener(PortForwardingEventListener listener) {
        if (listener == null) {
            return;
        }

        listeners.remove(PortForwardingEventListener.validateListener(listener));
    }

    @Override
    public Collection<PortForwardingEventListenerManager> getRegisteredManagers() {
        return managersHolder.isEmpty() ? Collections.emptyList() : new ArrayList<>(managersHolder);
    }

    @Override
    public boolean addPortForwardingEventListenerManager(PortForwardingEventListenerManager manager) {
        return managersHolder.add(Objects.requireNonNull(manager, "No manager"));
    }

    @Override
    public boolean removePortForwardingEventListenerManager(PortForwardingEventListenerManager manager) {
        if (manager == null) {
            return false;
        }

        return managersHolder.remove(manager);
    }

    @Override
    public Session getSession() {
        return sessionInstance;
    }

    public final ConnectionService getConnectionService() {
        return service;
    }

    protected Collection<PortForwardingEventListener> getDefaultListeners() {
        Collection<PortForwardingEventListener> defaultListeners = new ArrayList<>();
        defaultListeners.add(getPortForwardingEventListenerProxy());

        Session session = getSession();
        PortForwardingEventListener l = session.getPortForwardingEventListenerProxy();
        if (l != null) {
            defaultListeners.add(l);
        }

        FactoryManager manager = (session == null) ? null : session.getFactoryManager();
        l = (manager == null) ? null : manager.getPortForwardingEventListenerProxy();
        if (l != null) {
            defaultListeners.add(l);
        }

        return defaultListeners;
    }

    @Override
    public synchronized SshdSocketAddress startLocalPortForwarding(SshdSocketAddress local, SshdSocketAddress remote) throws IOException {
        Objects.requireNonNull(local, "Local address is null");
        ValidateUtils.checkTrue(local.getPort() >= 0, "Invalid local port: %s", local);
        Objects.requireNonNull(remote, "Remote address is null");

        if (isClosed()) {
            throw new IllegalStateException("TcpipForwarder is closed");
        }
        if (isClosing()) {
            throw new IllegalStateException("TcpipForwarder is closing");
        }

        InetSocketAddress bound = null;
        int port;
        signalEstablishingExplicitTunnel(local, remote, true);
        try {
            bound = doBind(local, sessionForwardHandler);
            port = bound.getPort();
            synchronized (localLock) {
                SshdSocketAddress prevRemote = localToRemote.get(port);
                if (prevRemote != null) {
                    throw new IOException("Multiple local port forwarding addressing on port=" + port
                            + ": current=" + remote + ", previous=" + prevRemote);
                }

                InetSocketAddress prevBound = boundLocals.get(port);
                if (prevBound != null) {
                    throw new IOException("Multiple local port forwarding bindings on port=" + port
                            + ": current=" + bound + ", previous=" + prevBound);
                }

                localToRemote.put(port, remote);
                boundLocals.put(port, bound);
            }
        } catch (IOException | RuntimeException e) {
            try {
                unbindLocalForwarding(local, remote, bound);
            } catch (IOException | RuntimeException err) {
                e.addSuppressed(err);
            }
            signalEstablishedExplicitTunnel(local, remote, true, null, e);
            throw e;
        }

        try {
            SshdSocketAddress result = new SshdSocketAddress(bound.getHostString(), port);
            if (log.isDebugEnabled()) {
                log.debug("startLocalPortForwarding(" + local + " -> " + remote + "): " + result);
            }
            signalEstablishedExplicitTunnel(local, remote, true, result, null);
            return result;
        } catch (IOException | RuntimeException e) {
            stopLocalPortForwarding(local);
            throw e;
        }
    }

    @Override
    public synchronized void stopLocalPortForwarding(SshdSocketAddress local) throws IOException {
        Objects.requireNonNull(local, "Local address is null");

        SshdSocketAddress remote;
        InetSocketAddress bound;
        int port = local.getPort();
        synchronized (localLock) {
            remote = localToRemote.remove(port);
            bound = boundLocals.remove(port);
        }

        unbindLocalForwarding(local, remote, bound);
    }

    protected void unbindLocalForwarding(
            SshdSocketAddress local, SshdSocketAddress remote, InetSocketAddress bound)
            throws IOException {
        if ((bound != null) && (acceptor != null)) {
            if (log.isDebugEnabled()) {
                log.debug("unbindLocalForwarding({} => {}) unbind {}", local, remote, bound);
            }

            SshdSocketAddress boundAddress = new SshdSocketAddress(bound);
            try {
                signalTearingDownExplicitTunnel(boundAddress, true, remote);
            } finally {
                try {
                     // acceptor.unbind(bound);
                    // CHANGED: DISABLE UNBIND...
                } catch (RuntimeException e) {
                    signalTornDownExplicitTunnel(boundAddress, true, remote, e);
                    throw e;
                }
            }

            signalTornDownExplicitTunnel(boundAddress, true, remote, null);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("unbindLocalForwarding({} => {}) no mapping({}) or acceptor({})",
                        local, remote, bound, acceptor);
            }
        }
    }

    @Override
    public synchronized SshdSocketAddress startRemotePortForwarding(SshdSocketAddress remote, SshdSocketAddress local) throws IOException {
        Objects.requireNonNull(local, "Local address is null");
        Objects.requireNonNull(remote, "Remote address is null");

        String remoteHost = remote.getHostName();
        int remotePort = remote.getPort();
        Session session = getSession();
        Buffer buffer = session.createBuffer(SshConstants.SSH_MSG_GLOBAL_REQUEST, remoteHost.length() + Long.SIZE);
        buffer.putString("tcpip-forward");
        buffer.putBoolean(true);    // want reply
        buffer.putString(remoteHost);
        buffer.putInt(remotePort);

        long timeout = session.getLongProperty(FORWARD_REQUEST_TIMEOUT, DEFAULT_FORWARD_REQUEST_TIMEOUT);
        Buffer result;
        int port;
        signalEstablishingExplicitTunnel(local, remote, false);
        try {
            result = session.request("tcpip-forward", buffer, timeout, TimeUnit.MILLISECONDS);
            if (result == null) {
                throw new SshException("Tcpip forwarding request denied by server");
            }
            port = (remotePort == 0) ? result.getInt() : remote.getPort();
            // TODO: Is it really safe to only store the local address after the request ?
            synchronized (remoteToLocal) {
                SshdSocketAddress prev = remoteToLocal.get(port);
                if (prev != null) {
                    throw new IOException("Multiple remote port forwarding bindings on port=" + port + ": current=" + remote + ", previous=" + prev);
                }
                remoteToLocal.put(port, local);
            }

        } catch (IOException | RuntimeException e) {
            try {
                stopRemotePortForwarding(remote);
            } catch (IOException | RuntimeException err) {
                e.addSuppressed(err);
            }
            signalEstablishedExplicitTunnel(local, remote, false, null, e);
            throw e;
        }

        try {
            SshdSocketAddress bound = new SshdSocketAddress(remoteHost, port);
            if (log.isDebugEnabled()) {
                log.debug("startRemotePortForwarding(" + remote + " -> " + local + "): " + bound);
            }

            signalEstablishedExplicitTunnel(local, remote, false, bound, null);
            return bound;
        } catch (IOException | RuntimeException e) {
            stopRemotePortForwarding(remote);
            throw e;
        }
    }

    @Override
    public synchronized void stopRemotePortForwarding(SshdSocketAddress remote) throws IOException {
        SshdSocketAddress bound;
        int port = remote.getPort();
        synchronized (remoteToLocal) {
            bound = remoteToLocal.remove(port);
        }

        if (bound != null) {
            if (log.isDebugEnabled()) {
                log.debug("stopRemotePortForwarding(" + remote + ") cancel forwarding to " + bound);
            }

            String remoteHost = remote.getHostName();
            Session session = getSession();
            Buffer buffer = session.createBuffer(SshConstants.SSH_MSG_GLOBAL_REQUEST, remoteHost.length() + Long.SIZE);
            buffer.putString("cancel-tcpip-forward");
            buffer.putBoolean(false);   // want reply
            buffer.putString(remoteHost);
            buffer.putInt(port);

            signalTearingDownExplicitTunnel(bound, false, remote);
            try {
                session.writePacket(buffer);
            } catch (IOException | RuntimeException e) {
                signalTornDownExplicitTunnel(bound, false, remote, e);
                throw e;
            }

            signalTornDownExplicitTunnel(bound, false, remote, null);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("stopRemotePortForwarding(" + remote + ") no binding found");
            }
        }
    }

    protected void signalTearingDownExplicitTunnel(
            SshdSocketAddress boundAddress, boolean localForwarding, SshdSocketAddress remote)
            throws IOException {
        try {
            invokePortEventListenerSignaller(l -> {
                signalTearingDownExplicitTunnel(l, boundAddress, localForwarding, remote);
                return null;
            });
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException("Failed (" + t.getClass().getSimpleName() + ")"
                        + " to signal tearing down explicit tunnel for local=" + localForwarding
                        + " on bound=" + boundAddress, t);
            }
        }
    }

    protected void signalTearingDownExplicitTunnel(
            PortForwardingEventListener listener, SshdSocketAddress boundAddress, boolean localForwarding, SshdSocketAddress remoteAddress)
            throws IOException {
        if (listener == null) {
            return;
        }

        listener.tearingDownExplicitTunnel(getSession(), boundAddress, localForwarding, remoteAddress);
    }

    protected void signalTornDownExplicitTunnel(
            SshdSocketAddress boundAddress, boolean localForwarding, SshdSocketAddress remoteAddress, Throwable reason)
            throws IOException {
        try {
            invokePortEventListenerSignaller(l -> {
                signalTornDownExplicitTunnel(l, boundAddress, localForwarding, remoteAddress, reason);
                return null;
            });
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException("Failed (" + t.getClass().getSimpleName() + ")"
                        + " to signal torn down explicit tunnel local=" + localForwarding
                        + " on bound=" + boundAddress, t);
            }
        }
    }

    protected void signalTornDownExplicitTunnel(
            PortForwardingEventListener listener, SshdSocketAddress boundAddress, boolean localForwarding, SshdSocketAddress remoteAddress, Throwable reason)
            throws IOException {
        if (listener == null) {
            return;
        }

        listener.tornDownExplicitTunnel(getSession(), boundAddress, localForwarding, remoteAddress, reason);
    }

    @Override
    public synchronized SshdSocketAddress startDynamicPortForwarding(SshdSocketAddress local) throws IOException {
        Objects.requireNonNull(local, "Local address is null");
        ValidateUtils.checkTrue(local.getPort() >= 0, "Invalid local port: %s", local);

        if (isClosed()) {
            throw new IllegalStateException("TcpipForwarder is closed");
        }
        if (isClosing()) {
            throw new IllegalStateException("TcpipForwarder is closing");
        }

        SocksProxy proxy = null;
        InetSocketAddress bound = null;
        int port;
        signalEstablishingDynamicTunnel(local);
        try {
            bound = doBind(local, new SocksProxy(getConnectionService()));
            port = bound.getPort();
            synchronized (dynamicLock) {
                SocksProxy prevProxy = dynamicLocal.get(port);
                if (prevProxy != null) {
                    throw new IOException("Multiple dynamic port mappings found for port=" + port
                            + ": current=" + proxy + ", previous=" + prevProxy);
                }

                InetSocketAddress prevBound = boundDynamic.get(port);
                if (prevBound != null) {
                    throw new IOException("Multiple dynamic port bindings found for port=" + port
                            + ": current=" + bound + ", previous=" + prevBound);
                }

                proxy = new SocksProxy(service);
                dynamicLocal.put(port, proxy);
                boundDynamic.put(port, bound);
            }
        } catch (IOException | RuntimeException e) {
            try {
                unbindDynamicForwarding(local, proxy, bound);
            } catch (IOException | RuntimeException err) {
                e.addSuppressed(err);
            }
            signalEstablishedDynamicTunnel(local, null, e);
            throw e;
        }

        try {
            SshdSocketAddress result = new SshdSocketAddress(bound.getHostString(), port);
            if (log.isDebugEnabled()) {
                log.debug("startDynamicPortForwarding(" + local + "): " + result);
            }

            signalEstablishedDynamicTunnel(local, result, null);
            return result;
        } catch (IOException | RuntimeException e) {
            stopDynamicPortForwarding(local);
            throw e;
        }
    }

    protected void signalEstablishedDynamicTunnel(
            SshdSocketAddress local, SshdSocketAddress boundAddress, Throwable reason)
            throws IOException {
        try {
            invokePortEventListenerSignaller(l -> {
                signalEstablishedDynamicTunnel(l, local, boundAddress, reason);
                return null;
            });
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException("Failed (" + t.getClass().getSimpleName() + ")"
                        + " to signal establishing dynamic tunnel for local=" + local
                        + " on bound=" + boundAddress, t);
            }
        }
    }

    protected void signalEstablishedDynamicTunnel(PortForwardingEventListener listener,
                                                  SshdSocketAddress local, SshdSocketAddress boundAddress, Throwable reason)
            throws IOException {
        if (listener == null) {
            return;
        }

        listener.establishedDynamicTunnel(getSession(), local, boundAddress, reason);
    }

    protected void signalEstablishingDynamicTunnel(SshdSocketAddress local) throws IOException {
        try {
            invokePortEventListenerSignaller(l -> {
                signalEstablishingDynamicTunnel(l, local);
                return null;
            });
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException("Failed (" + t.getClass().getSimpleName() + ")"
                        + " to signal establishing dynamic tunnel for local=" + local, t);
            }
        }
    }

    protected void signalEstablishingDynamicTunnel(PortForwardingEventListener listener, SshdSocketAddress local) throws IOException {
        if (listener == null) {
            return;
        }

        listener.establishingDynamicTunnel(getSession(), local);
    }

    @Override
    public synchronized void stopDynamicPortForwarding(SshdSocketAddress local) throws IOException {
        SocksProxy proxy;
        InetSocketAddress bound;
        int port = local.getPort();
        synchronized (dynamicLock) {
            proxy = dynamicLocal.remove(port);
            bound = boundDynamic.remove(port);
        }

        unbindDynamicForwarding(local, proxy, bound);
    }

    protected void unbindDynamicForwarding(
            SshdSocketAddress local, SocksProxy proxy, InetSocketAddress bound) throws IOException {
        boolean debugEnabled = log.isDebugEnabled();
        if ((bound != null) || (proxy != null)) {

            try {
                signalTearingDownDynamicTunnel(local);
            } finally {
                try {
                    try {
                        if (proxy != null) {
                            if (debugEnabled) {
                                log.debug("stopDynamicPortForwarding({}) close proxy={}", local, proxy);
                            }

                            proxy.close(true);
                        }
                    } finally {
                        if ((bound != null) && (acceptor != null)) {
                            if (debugEnabled) {
                                log.debug("stopDynamicPortForwarding({}) unbind address={}", local, bound);
                            }
                            acceptor.unbind(bound);
                        } else {
                            if (debugEnabled) {
                                log.debug("stopDynamicPortForwarding({}) no acceptor({}) or no binding({})",
                                        local, acceptor, bound);
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    signalTornDownDynamicTunnel(local, e);
                    throw e;
                }
            }

            signalTornDownDynamicTunnel(local, null);
        } else {
            if (debugEnabled) {
                log.debug("stopDynamicPortForwarding({}) no binding found", local);
            }
        }
    }

    protected void signalTearingDownDynamicTunnel(SshdSocketAddress address) throws IOException {
        try {
            invokePortEventListenerSignaller(l -> {
                signalTearingDownDynamicTunnel(l, address);
                return null;
            });
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException("Failed (" + t.getClass().getSimpleName() + ")"
                        + " to signal tearing down dynamic tunnel for address=" + address, t);
            }
        }
    }

    protected void signalTearingDownDynamicTunnel(PortForwardingEventListener listener, SshdSocketAddress address) throws IOException {
        if (listener == null) {
            return;
        }

        listener.tearingDownDynamicTunnel(getSession(), address);
    }

    protected void signalTornDownDynamicTunnel(SshdSocketAddress address, Throwable reason) throws IOException {
        try {
            invokePortEventListenerSignaller(l -> {
                signalTornDownDynamicTunnel(l, address, reason);
                return null;
            });
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException("Failed (" + t.getClass().getSimpleName() + ")"
                        + " to signal torn down dynamic tunnel for address=" + address, t);
            }
        }
    }

    protected void signalTornDownDynamicTunnel(
            PortForwardingEventListener listener, SshdSocketAddress address, Throwable reason)
            throws IOException {
        if (listener == null) {
            return;
        }

        listener.tornDownDynamicTunnel(getSession(), address, reason);
    }

    @Override
    public synchronized SshdSocketAddress getForwardedPort(int remotePort) {
        synchronized (remoteToLocal) {
            return remoteToLocal.get(remotePort);
        }
    }

    @Override
    public synchronized SshdSocketAddress localPortForwardingRequested(SshdSocketAddress local) throws IOException {

        // CHANGED !!
//        local = new SshdSocketAddress(local.getHostName(), local.getPort() + portSequence.getAndIncrement()); // avoid conflics

        Objects.requireNonNull(local, "Local address is null");
        ValidateUtils.checkTrue(local.getPort() >= 0, "Invalid local port: %s", local);

        Session session = getSession();
        FactoryManager manager = Objects.requireNonNull(session.getFactoryManager(), "No factory manager");
        TcpForwardingFilter filter = manager.getTcpForwardingFilter();
        try {
            if ((filter == null) || (!filter.canListen(local, session))) {
                if (log.isDebugEnabled()) {
                    log.debug("localPortForwardingRequested(" + session + ")[" + local + "][haveFilter=" + (filter != null) + "] rejected");
                }
                return null;
            }
        } catch (Error e) {
            log.warn("localPortForwardingRequested({})[{}] failed ({}) to consult forwarding filter: {}",
                    session, local, e.getClass().getSimpleName(), e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("localPortForwardingRequested(" + this + ")[" + local + "] filter consultation failure details", e);
            }
            throw new RuntimeSshException(e);
        }

        signalEstablishingExplicitTunnel(local, null, true);

        sessionForwardHandler.register(service, local.getHostName());

        SshdSocketAddress result;
        try {
            InetSocketAddress bound = doBind(local, sessionForwardHandler);
            result = new SshdSocketAddress(bound.getHostString(), bound.getPort());
            if (log.isDebugEnabled()) {
                log.debug("localPortForwardingRequested(" + local + "): " + result);
            }

            boolean added;
            synchronized (sessionForwardHandler.getLocalForwards()) {
                // NOTE !!! it is crucial to use the bound address host name first
                added = sessionForwardHandler.getLocalForwards().add(new LocalForwardingEntry(/*CHANGED*/local.getHostName(), local.getHostName(), result.getPort()));

                System.err.println("add to localForwards : " + local.getHostName() + ", result.getPort(): "+ result.getPort());

            }

            if (!added) {
                throw new IOException("Failed to add local port forwarding entry for " + local + " -> " + result);
            }
        } catch (IOException | RuntimeException e) {
            try {
                localPortForwardingCancelled(local);
            } catch (IOException | RuntimeException err) {
                e.addSuppressed(e);
            }
            signalEstablishedExplicitTunnel(local, null, true, null, e);
            throw e;
        }

        try {
            signalEstablishedExplicitTunnel(local, null, true, result, null);
            return result;
        } catch (IOException | RuntimeException e) {
            throw e;
        }
    }

    @Override
    public synchronized void localPortForwardingCancelled(SshdSocketAddress local) throws IOException {
        LocalForwardingEntry entry;
        synchronized (sessionForwardHandler.getLocalForwards()) {
            entry = LocalForwardingEntry.findMatchingEntry(local.getHostName(), local.getPort(), sessionForwardHandler.getLocalForwards());
            if (entry != null) {
                sessionForwardHandler.getLocalForwards().remove(entry);
            }
        }

        sessionForwardHandler.unregister(service);

        if ((entry != null) && (acceptor != null)) {
            if (log.isDebugEnabled()) {
                log.debug("localPortForwardingCancelled(" + local + ") unbind " + entry);
            }

            signalTearingDownExplicitTunnel(entry, true, null);
            try {
                // acceptor.unbind(entry.toInetSocketAddress());
                // CHANGED !!!
            } catch (RuntimeException e) {
                signalTornDownExplicitTunnel(entry, true, null, e);
                throw e;
            }

            signalTornDownExplicitTunnel(entry, true, null, null);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("localPortForwardingCancelled(" + local + ") no match/acceptor: " + entry);
            }
        }
    }

    protected void signalEstablishingExplicitTunnel(
            SshdSocketAddress local, SshdSocketAddress remote, boolean localForwarding)
            throws IOException {
        try {
            invokePortEventListenerSignaller(l -> {
                signalEstablishingExplicitTunnel(l, local, remote, localForwarding);
                return null;
            });
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException("Failed (" + t.getClass().getSimpleName() + ")"
                        + " to signal establishing explicit tunnel for local=" + local
                        + ", remote=" + remote + ", localForwarding=" + localForwarding, t);
            }
        }
    }

    protected void signalEstablishingExplicitTunnel(
            PortForwardingEventListener listener, SshdSocketAddress local, SshdSocketAddress remote, boolean localForwarding)
            throws IOException {
        if (listener == null) {
            return;
        }

        listener.establishingExplicitTunnel(getSession(), local, remote, localForwarding);
    }

    protected void signalEstablishedExplicitTunnel(
            SshdSocketAddress local, SshdSocketAddress remote, boolean localForwarding,
            SshdSocketAddress boundAddress, Throwable reason)
            throws IOException {
        try {
            invokePortEventListenerSignaller(l -> {
                signalEstablishedExplicitTunnel(l, local, remote, localForwarding, boundAddress, reason);
                return null;
            });
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException("Failed (" + t.getClass().getSimpleName() + ")"
                        + " to signal established explicit tunnel for local=" + local
                        + ", remote=" + remote + ", localForwarding=" + localForwarding
                        + ", bound=" + boundAddress, t);
            }
        }
    }

    protected void signalEstablishedExplicitTunnel(PortForwardingEventListener listener,
                                                   SshdSocketAddress local, SshdSocketAddress remote, boolean localForwarding,
                                                   SshdSocketAddress boundAddress, Throwable reason)
            throws IOException {
        if (listener == null) {
            return;
        }

        listener.establishedExplicitTunnel(getSession(), local, remote, localForwarding, boundAddress, reason);
    }

    protected void invokePortEventListenerSignaller(Invoker<PortForwardingEventListener, Void> invoker) throws Throwable {
        Throwable err = null;
        try {
            invokePortEventListenerSignallerListeners(getDefaultListeners(), invoker);
        } catch (Throwable t) {
            Throwable e = GenericUtils.peelException(t);
            err = GenericUtils.accumulateException(err, e);
        }

        try {
            invokePortEventListenerSignallerHolders(managersHolder, invoker);
        } catch (Throwable t) {
            Throwable e = GenericUtils.peelException(t);
            err = GenericUtils.accumulateException(err, e);
        }

        if (err != null) {
            throw err;
        }
    }

    protected void invokePortEventListenerSignallerListeners(
            Collection<? extends PortForwardingEventListener> listeners, Invoker<PortForwardingEventListener, Void> invoker)
            throws Throwable {
        if (GenericUtils.isEmpty(listeners)) {
            return;
        }

        Throwable err = null;
        // Need to go over the hierarchy (session, factory managed, connection service, etc...)
        for (PortForwardingEventListener l : listeners) {
            if (l == null) {
                continue;
            }

            try {
                invoker.invoke(l);
            } catch (Throwable t) {
                Throwable e = GenericUtils.peelException(t);
                err = GenericUtils.accumulateException(err, e);
            }
        }

        if (err != null) {
            throw err;
        }
    }

    protected void invokePortEventListenerSignallerHolders(
            Collection<? extends PortForwardingEventListenerManager> holders, Invoker<PortForwardingEventListener, Void> invoker)
            throws Throwable {
        if (GenericUtils.isEmpty(holders)) {
            return;
        }

        Throwable err = null;
        // Need to go over the hierarchy (session, factory managed, connection service, etc...)
        for (PortForwardingEventListenerManager m : holders) {
            try {
                PortForwardingEventListener listener = m.getPortForwardingEventListenerProxy();
                if (listener != null) {
                    invoker.invoke(listener);
                }
            } catch (Throwable t) {
                Throwable e = GenericUtils.peelException(t);
                err = GenericUtils.accumulateException(err, e);
            }

            if (m instanceof PortForwardingEventListenerManagerHolder) {
                try {
                    invokePortEventListenerSignallerHolders(((PortForwardingEventListenerManagerHolder) m).getRegisteredManagers(), invoker);
                } catch (Throwable t) {
                    Throwable e = GenericUtils.peelException(t);
                    err = GenericUtils.accumulateException(err, e);
                }
            }
        }

        if (err != null) {
            throw err;
        }
    }

    @Override
    protected synchronized Closeable getInnerCloseable() {
        return builder().parallel(toString(), dynamicLocal.values()).close(acceptor).build();
    }

    @Override
    protected void preClose() {
        this.listeners.clear();
        this.managersHolder.clear();
        super.preClose();
    }

    /**
     * @param address        The request bind address
     * @param handler A {@link IoHandler} if necessary
     * @return The {@link InetSocketAddress} to which the binding occurred
     * @throws IOException If failed to bind
     */
    private InetSocketAddress doBind(SshdSocketAddress address, IoHandler handler) throws IOException {
        if (acceptor == null) {
            Session session = getSession();
            FactoryManager manager = Objects.requireNonNull(session.getFactoryManager(), "No factory manager");
            IoServiceFactory factory = Objects.requireNonNull(manager.getIoServiceFactory(), "No I/O service factory");
//            IoHandler handler = handlerFactory.create();
            acceptor = factory.createAcceptor(handler);
        }

        // TODO find a better way to determine the resulting bind address - what if multi-threaded calls...
        Set<SocketAddress> before = acceptor.getBoundAddresses();

        // Reuse addr
        if(!before.isEmpty()) return (InetSocketAddress) before.iterator().next();

        try {
            InetSocketAddress bindAddress = address.toInetSocketAddress();
            acceptor.bind(bindAddress);

            Set<SocketAddress> after = acceptor.getBoundAddresses();
            if (GenericUtils.size(after) > 0) {
                after.removeAll(before);
            }
            if (GenericUtils.isEmpty(after)) {
                throw new IOException("Error binding to " + address + "[" + bindAddress + "]: no local addresses bound");
            }

            if (after.size() > 1) {
                throw new IOException("Multiple local addresses have been bound for " + address + "[" + bindAddress + "]");
            }
            return (InetSocketAddress) GenericUtils.head(after);
        } catch (IOException bindErr) {
            Set<SocketAddress> after = acceptor.getBoundAddresses();
            if (GenericUtils.isEmpty(after)) {
                close();
            }
            throw bindErr;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getSession() + "]";
    }



    @Override
    public SshdSocketAddress getBoundLocalPortForward(int port) {
        ValidateUtils.checkTrue(port > 0, "Invalid local port: %d", port);

        Integer portKey = Integer.valueOf(port);
        synchronized (localToRemote) {
            return localToRemote.get(portKey);
        }
    }

    @Override
    public List<Map.Entry<Integer, SshdSocketAddress>> getLocalForwardsBindings() {
        synchronized (localToRemote) {
            return localToRemote.isEmpty()
                    ? Collections.emptyList()
                    : localToRemote.entrySet()
                    .stream()  // return an immutable clone to avoid 'setValue' calls on a shared instance
                    .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()))
                    .collect(Collectors.toCollection(() -> new ArrayList<>(localToRemote.size())));
        }
    }

    @Override
    public NavigableSet<Integer> getStartedLocalPortForwards() {
        synchronized (localToRemote) {
            if (localToRemote.isEmpty()) {
                return Collections.emptyNavigableSet();
            }

            return GenericUtils.asSortedSet(localToRemote.keySet());
        }
    }

    @Override
    public List<Map.Entry<Integer, SshdSocketAddress>> getRemoteForwardsBindings() {
        synchronized (remoteToLocal) {
            return remoteToLocal.isEmpty()
                    ? Collections.emptyList()
                    : remoteToLocal.entrySet()
                    .stream()  // return an immutable clone to avoid 'setValue' calls on a shared instance
                    .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()))
                    .collect(Collectors.toCollection(() -> new ArrayList<>(remoteToLocal.size())));
        }
    }

    @Override
    public SshdSocketAddress getBoundRemotePortForward(int port) {
        ValidateUtils.checkTrue(port > 0, "Invalid remote port: %d", port);

        Integer portKey = Integer.valueOf(port);
        synchronized (remoteToLocal) {
            return remoteToLocal.get(portKey);
        }
    }

    @Override
    public NavigableSet<Integer> getStartedRemotePortForwards() {
        synchronized (remoteToLocal) {
            if (remoteToLocal.isEmpty()) {
                return Collections.emptyNavigableSet();
            }

            return GenericUtils.asSortedSet(remoteToLocal.keySet());
        }
    }
}


