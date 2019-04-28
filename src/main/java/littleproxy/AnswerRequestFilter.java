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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.littleshoot.proxy.HttpFiltersAdapter;

import java.io.UnsupportedEncodingException;

public class AnswerRequestFilter extends HttpFiltersAdapter {
    private final String answer;

	public AnswerRequestFilter(HttpRequest originalRequest, String answer) {
		super(originalRequest, null);
		this.answer = answer;
	}

	@Override
	public HttpResponse clientToProxyRequest(HttpObject httpObject) {
		ByteBuf buffer = null;
		try {
			buffer = Unpooled.wrappedBuffer(answer.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
		}
		HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer);
		HttpHeaders.setContentLength(response, buffer.readableBytes());
		HttpHeaders.setHeader(response, HttpHeaders.Names.CONTENT_TYPE, "text/html");
		return response;
	}
}