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

import io.netty.handler.codec.http.*;
import io.netty.util.internal.StringUtil;

import java.util.Map;

public final class HttpMessageUtil {

    static StringBuilder appendRequest(StringBuilder buf, HttpRequest req) {
        appendCommon(buf, req);
        appendInitialLine(buf, req);
        appendHeaders(buf, req.headers());
        removeLastNewLine(buf);
        return buf;
    }

    static StringBuilder appendResponse(StringBuilder buf, HttpResponse res) {
        appendCommon(buf, res);
        appendInitialLine(buf, res);
        appendHeaders(buf, res.headers());
        removeLastNewLine(buf);
        return buf;
    }

    private static void appendCommon(StringBuilder buf, HttpMessage msg) {

    }

    static StringBuilder appendFullRequest(StringBuilder buf, FullHttpRequest req) {
        appendFullCommon(buf, req);
        appendInitialLine(buf, req);
        appendHeaders(buf, req.headers());
        appendHeaders(buf, req.trailingHeaders());
        removeLastNewLine(buf);
        return buf;
    }

    static StringBuilder appendFullResponse(StringBuilder buf, FullHttpResponse res) {
        appendFullCommon(buf, res);
        appendInitialLine(buf, res);
        appendHeaders(buf, res.headers());
        appendHeaders(buf, res.trailingHeaders());
        removeLastNewLine(buf);
        return buf;
    }

    private static void appendFullCommon(StringBuilder buf, FullHttpMessage msg) {
    }

    private static void appendInitialLine(StringBuilder buf, HttpRequest req) {
        buf.append(req.method());
        buf.append(' ');
        buf.append(req.uri());
        buf.append(' ');
        buf.append(req.protocolVersion());
        buf.append(StringUtil.NEWLINE);
    }

    private static void appendInitialLine(StringBuilder buf, HttpResponse res) {
        buf.append(res.protocolVersion());
        buf.append(' ');
        buf.append(res.status());
        buf.append(StringUtil.NEWLINE);
    }

    private static void appendHeaders(StringBuilder buf, HttpHeaders headers) {
        for (Map.Entry<String, String> e: headers) {
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }

    private static void removeLastNewLine(StringBuilder buf) {
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
    }

    private HttpMessageUtil() { }
}