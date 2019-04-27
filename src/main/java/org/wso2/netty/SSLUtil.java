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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;

public class SSLUtil {
	private static String KEY_STORE_TYPE = "JKS";
	private static String TRUST_STORE_TYPE = "JKS";
	private static String KEY_MANAGER_TYPE = "SunX509";
	private static String TRUST_MANAGER_TYPE = "SunX509";
	private static String PROTOCOL = "TLS";

	private static SSLContext serverSSLCtx = null;
	private static SSLContext clientSSLCtx = null;

	private static final Logger LOGGER = LoggerFactory.getLogger(SSLUtil.class);

	public static SSLContext createServerSSLContext(final String keyStoreLocation,
	                                                final String keyStorePwd) {
		try {
			if (serverSSLCtx == null) {
				KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
				keyStore.load(new FileInputStream(keyStoreLocation), keyStorePwd.toCharArray());
				KeyManagerFactory keyManagerFactory =
				                                      KeyManagerFactory.getInstance(KEY_MANAGER_TYPE);
				keyManagerFactory.init(keyStore, keyStorePwd.toCharArray());
				serverSSLCtx = SSLContext.getInstance(PROTOCOL);
				serverSSLCtx.init(keyManagerFactory.getKeyManagers(), null, null);
			}
		} catch (UnrecoverableKeyException e) {
			LOGGER.error("Exception was thrown while building the server SSL Context", e);
		} catch (KeyManagementException e) {
			LOGGER.error("Exception was thrown while building the server SSL Context", e);
		} catch (KeyStoreException e) {
			LOGGER.error("Exception was thrown while building the server SSL Context", e);
		} catch (NoSuchAlgorithmException e) {
			LOGGER.error("Exception was thrown while building the server SSL Context", e);
		} catch (CertificateException e) {
			LOGGER.error("Exception was thrown while building the server SSL Context", e);
		} catch (FileNotFoundException e) {
			LOGGER.error("Exception was thrown while building the server SSL Context", e);
		} catch (IOException e) {
			LOGGER.error("Exception was thrown while building the server SSL Context", e);
		}

		return serverSSLCtx;
	}

	public static SSLContext createClientSSLContext(final String trustStoreLocation,
	                                                final String trustStorePwd) {
		try {
			if (clientSSLCtx == null) {
				KeyStore trustStore = KeyStore.getInstance(TRUST_STORE_TYPE);
				trustStore.load(new FileInputStream(trustStoreLocation),
				                trustStorePwd.toCharArray());
				TrustManagerFactory trustManagerFactory =
				                                          TrustManagerFactory.getInstance(TRUST_MANAGER_TYPE);
				trustManagerFactory.init(trustStore);
				clientSSLCtx = SSLContext.getInstance(PROTOCOL);
				clientSSLCtx.init(null, trustManagerFactory.getTrustManagers(), null);
			}
		} catch (KeyManagementException e) {
			LOGGER.error("Exception was thrown while building the client SSL Context", e);
		} catch (KeyStoreException e) {
			LOGGER.error("Exception was thrown while building the client SSL Context", e);
		} catch (NoSuchAlgorithmException e) {
			LOGGER.error("Exception was thrown while building the client SSL Context", e);
		} catch (CertificateException e) {
			LOGGER.error("Exception was thrown while building the client SSL Context", e);
		} catch (FileNotFoundException e) {
			LOGGER.error("Exception was thrown while building the client SSL Context", e);
		} catch (IOException e) {
			LOGGER.error("Exception was thrown while building the client SSL Context", e);
		}

		return clientSSLCtx;

	}

}
