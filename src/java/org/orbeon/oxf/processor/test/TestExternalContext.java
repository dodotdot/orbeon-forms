package org.orbeon.oxf.processor.test;

import org.apache.commons.fileupload.FileItem;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.EmailProcessor;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.URLRewriterUtils;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.xml.transform.sax.SAXSource;
import java.io.*;
import java.security.Principal;
import java.util.*;

/**
 * ExternalContext used by the TestScriptProcessor. It is configurable with an XML document representing
 * the request.
 */
public class TestExternalContext implements ExternalContext  {

    private static final Logger logger = LoggerFactory.createLogger(TestExternalContext.class);

    private PipelineContext pipelineContext;
    private Document requestDocument;

    private Request request;
    private Response response;

    private Map attributesMap;

    public TestExternalContext(PipelineContext pipelineContext, Document requestDocument) {
        this.pipelineContext = pipelineContext;
        this.requestDocument = requestDocument;
    }

    private class Request implements ExternalContext.Request {

        private Map attributesMap;
        private Map parameterMap;
        private Map headerMap;
        private Map headerValuesMap;

        private InputStream bodyInputStream;
        private String bodyContentType;
        private String bodyEncoding;
        private long bodyContentLength;
        private Reader bodyReader;
        private boolean getInputStreamCalled;
        private boolean getReaderCalled;

        public Map getAttributesMap() {
            if (attributesMap == null) {
                attributesMap = new LinkedHashMap();
            }
            return attributesMap;
        }

        public String getAuthType() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/auth-type");
        }

        public String getCharacterEncoding() {
            if (bodyInputStream == null)
                setupBody();
            return bodyEncoding;
        }

        public String getContainerType() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/container-type");
        }

        public String getContainerNamespace() {
            return "";
        }

        public int getContentLength() {
            if (bodyInputStream == null)
                setupBody();
            return (int) bodyContentLength;
        }

        public String getContentType() {
            if (bodyInputStream == null)
                setupBody();
            return bodyContentType;
        }

        public InputStream getInputStream() throws IOException {
            if (getReaderCalled)
                throw new IllegalStateException("Cannot call getInputStream() after getReader() has been called.");
            if (bodyInputStream == null)
                setupBody();
            getInputStreamCalled = true;
            return bodyInputStream;
        }

        private void setupBody() {
            try {
                Element bodyNode = (Element) XPathUtils.selectSingleNode(requestDocument, "/*/body");
                if (bodyNode != null) {
                    String contentTypeAttribute = bodyNode.attributeValue("content-type");
                    final String contentType = NetUtils.getContentTypeMediaType(contentTypeAttribute);
                    final String charset = NetUtils.getContentTypeCharset(contentTypeAttribute);

                    String hrefAttribute = bodyNode.attributeValue("href");
                    // TODO: Support same scenarios as Email processor
                    if (hrefAttribute == null)
                        throw new OXFException("Inline content not implemented yet.");

                    LocationData locationData = (LocationData) bodyNode.getData();
                    String systemId = locationData == null ? null : locationData.getSystemID();

                    SAXSource saxSource = EmailProcessor.getSAXSource(null, pipelineContext, hrefAttribute, systemId, contentType);
                    FileItem content = EmailProcessor.handleStreamedPartContent(pipelineContext, saxSource);

                    if (!(XMLUtils.isTextContentType(contentType) || XMLUtils.isXMLMediatype(contentType))) {
                        // This is binary content
                        if (content instanceof FileItem) {
                            final FileItem fileItem = (FileItem) content;

                            bodyInputStream = fileItem.getInputStream();
                            bodyContentType = contentType;
                            bodyContentLength = fileItem.getSize();
                        } else {
                            // TODO
                            throw new OXFException("Not implemented yet.");
//                            byte[] data = XMLUtils.base64StringToByteArray((String) content);
//
//                            bodyInputStream = new ByteArrayInputStream(data);
//                            bodyContentType = contentType;
//                            bodyContentLength = data.length;
                        }
                    } else {
                        // This is text content
                        if (content instanceof FileItem) {
                            // The text content was encoded when written to the FileItem
                            final FileItem fileItem = (FileItem) content;

                            bodyInputStream = fileItem.getInputStream();
                            bodyContentType = contentType;
                            bodyEncoding = charset;
                            bodyContentLength = fileItem.getSize();

                        } else {
                            // TODO
                            throw new OXFException("Not implemented yet.");

//                            final String s = (String) content
//                            byte[] bytes = s.getBytes(charset);
//
//                            bodyInputStream = new ByteArrayInputStream(bytes);
//                            bodyContentType = contentType;
//                            bodyEncoding = charset;
//                            bodyContentLength = bytes.length;
                        }
                    }
                }
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }

        public Reader getReader() throws IOException {
            if (getInputStreamCalled)
                throw new IllegalStateException("Cannot call getReader() after getInputStream() has been called.");
            if (bodyInputStream == null)
                setupBody();
            if (bodyReader == null)
                bodyReader = new InputStreamReader(bodyInputStream, bodyEncoding);
            getReaderCalled = true;
            return bodyReader;
        }

        public String getContextPath() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/context-path");
        }

        public Map getHeaderMap() {
            if (headerMap == null) {
                Map map = new LinkedHashMap();
                for (Iterator i = XPathUtils.selectIterator(requestDocument, "/*/headers/header"); i.hasNext();) {
                    Element e = (Element) i.next();
                    String name = XPathUtils.selectStringValueNormalize(e, "name");
                    String value = XPathUtils.selectStringValueNormalize(e, "value[1]");
                    map.put(name, value);
                }
                headerMap = Collections.unmodifiableMap(map);
            }
            return headerMap;
        }

        public Map getHeaderValuesMap() {
            if (headerValuesMap == null) {
                final Map map = new LinkedHashMap();
                for (Iterator i = XPathUtils.selectIterator(requestDocument, "/*/headers/header"); i.hasNext();) {
                    final Element e = (Element) i.next();
                    final String name = XPathUtils.selectStringValueNormalize(e, "name");
                    for (Iterator j = XPathUtils.selectIterator(e, "value"); j.hasNext();) {
                        final Element valueElement = (Element) j.next();
                        final String value = XPathUtils.selectStringValueNormalize(valueElement, ".");
                        NetUtils.addValueToStringArrayMap(map, name, value);
                    }
                }
                headerValuesMap = Collections.unmodifiableMap(map);
            }
            return headerValuesMap;
        }

        public Locale getLocale() {
            // NIY
            return null;
        }

        public Enumeration getLocales() {
            // NIY
            return null;
        }

        public String getMethod() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/method");
        }

        public Map getParameterMap() {
            if (parameterMap == null) {
                final Map map = new LinkedHashMap();
                for (Iterator i = XPathUtils.selectIterator(requestDocument, "/*/parameters/parameter"); i.hasNext();) {
                    final Element e = (Element) i.next();
                    final String name = XPathUtils.selectStringValueNormalize(e, "name");
                    for (Iterator j = XPathUtils.selectIterator(e, "value"); j.hasNext();) {
                        final Element valueElement = (Element) j.next();
                        final String value = XPathUtils.selectStringValueNormalize(valueElement, ".");
                        NetUtils.addValueToObjectArrayMap(map, name, value);
                    }
                }
                parameterMap = Collections.unmodifiableMap(map);
            }
            return parameterMap;
        }

        public String getPathInfo() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/path-info");
        }

        public String getPathTranslated() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/path-translated");
        }

        public String getProtocol() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/protocol");
        }

        public String getQueryString() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/query-string");
        }

        public String getRemoteAddr() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/remote-addr");
        }

        public String getRemoteHost() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/remote-host");
        }

        public String getRemoteUser() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/remote-user");
        }

        public String getRequestedSessionId() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/requested-session-id");
        }

        public String getRequestPath() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/request-path");
        }

        public String getRequestURI() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/request-uri");
        }

        public String getRequestURL() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/request-url");
        }

        public String getScheme() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/scheme");
        }

        public String getServerName() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/server-name");
        }

        public int getServerPort() {
            return ProcessorUtils.selectIntValue(requestDocument, "/*/server-port", 80);
        }

        public String getServletPath() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/servlet-path");
        }

        public Principal getUserPrincipal() {
            // NIY
            return null;
        }

        public boolean isRequestedSessionIdValid() {
            // NIY
            return false;
        }

        public boolean isSecure() {
            return ProcessorUtils.selectBooleanValue(requestDocument, "/*/is-secure", false);
        }

        public boolean isUserInRole(String role) {
            // NIY
            return false;
        }

        public void sessionInvalidate() {
            // NIY
        }

        public String getPortletMode() {
            return null;
        }

        public String getWindowState() {
            return null;
        }

        public Object getNativeRequest() {
            return TestExternalContext.this.getNativeRequest();
        }
    }

    private class Response implements ExternalContext.Response {
        public void addHeader(String name, String value) {
        }

        public boolean checkIfModifiedSince(long lastModified, boolean allowOverride) {
            return true;
        }

        public String getCharacterEncoding() {
            return null;
        }

        public String getNamespacePrefix() {
            return null;
        }

        public OutputStream getOutputStream() throws IOException {
            return null;
        }

        public PrintWriter getWriter() throws IOException {
            return null;
        }

        public boolean isCommitted() {
            return false;
        }

        public void reset() {
        }

        public String rewriteActionURL(String urlString) {
            return rewriteResourceURL(urlString, false);
        }

        public String rewriteRenderURL(String urlString) {
            return rewriteResourceURL(urlString, false);
        }

        public String rewriteActionURL(String urlString, String portletMode, String windowState) {
            return rewriteResourceURL(urlString, false);
        }

        public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
            return rewriteResourceURL(urlString, false);
        }

        public String rewriteResourceURL(String urlString, boolean absolute) {
            return rewriteResourceURL(urlString, absolute ? REWRITE_MODE_ABSOLUTE : REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteResourceURL(String urlString, int rewriteMode) {
            return URLRewriterUtils.rewriteURL(getRequest(), urlString, rewriteMode);
        }

        public void sendError(int len) throws IOException {
        }

        public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal, boolean isNoRewrite) throws IOException {
        }

        public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {
        }

        public void setResourceCaching(long lastModified, long expires) {
        }

        public void setContentLength(int len) {
        }

        public void setContentType(String contentType) {
        }

        public void setHeader(String name, String value) {
        }

        public void setStatus(int status) {
        }

        public void setTitle(String title) {
        }

        public Object getNativeResponse() {
            return TestExternalContext.this.getNativeResponse();
        }
    }

    public ExternalContext.Request getRequest() {
        if (request == null)
            request = new Request();
        return request;
    }

    public ExternalContext.Response getResponse() {
        if (response == null)
            response = new Response();
        return response;
    }

    private ExternalContext.Session session;

    public ExternalContext.Session getSession(boolean create) {
        if (session == null && create) {
            session = new ExternalContext.Session() {
                final Map attributes = new LinkedHashMap();
                public long getCreationTime() {
                    return System.currentTimeMillis();
                }

                public String getId() {
                    // TODO
                    return null;
                }

                public long getLastAccessedTime() {
                    // TODO
                    return 0;
                }

                public int getMaxInactiveInterval() {
                    // TODO
                    return 0;
                }

                public void invalidate() {
                    // TODO
                }

                public boolean isNew() {
                    // TODO
                    return false;
                }

                public void setMaxInactiveInterval(int interval) {
                    // TODO
                }

                public Map getAttributesMap() {
                    return attributes;
                }

                public Map getAttributesMap(int scope) {
                    return attributes;
                }

                public void addListener(SessionListener sessionListener) {
                    // TODO
                }

                public void removeListener(SessionListener sessionListener) {
                    // TODO
                }
            };
        }
        return session;
    }

    public ExternalContext.Application getApplication() {
        // NIY
        return null;
    }

    public RequestDispatcher getRequestDispatcher(String path, boolean isContextRelative) {
        // NIY
        return null;
    }

    public RequestDispatcher getNamedDispatcher(String name) {
        // NIY
        return null;
    }

    public String getStartLoggerString() {
        return "Running test processor";
    }

    public String getEndLoggerString() {
        return "Done running test processor";
    }

    public Object getNativeRequest() {
        return null;
    }

    public Object getNativeResponse() {
        return null;
    }

    public Object getNativeSession(boolean flag) {
        return null;
    }

    public Map getAttributesMap() {
        if (attributesMap == null) {
            attributesMap = new LinkedHashMap();
        }
        return attributesMap;
    }

    public Map getInitAttributesMap() {
        return Collections.EMPTY_MAP;
    }

    public Object getNativeContext() {
        return null;
    }

    public String getRealPath(String path) {
        // NIY
        return null;
    }

    public void log(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    public void log(String msg) {
        logger.info(msg);
    }

    public String rewriteServiceURL(String urlString, boolean forceAbsolute) {
        return URLRewriterUtils.rewriteServiceURL(getRequest(), urlString, forceAbsolute);
    }
}
