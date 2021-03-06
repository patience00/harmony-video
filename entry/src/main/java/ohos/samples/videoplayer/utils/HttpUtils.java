package ohos.samples.videoplayer.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.http.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 * HTTP ?????????
 *
 * @author linch
 * @date 2019/10/29
 * @since v1.0.0
 */
public final class HttpUtils {

    /**
     * ????????????
     */
    private HttpRequestBase request;
    /**
     * Post, Put, Patch???????????????
     */
    private EntityBuilder builder; // Post, Put???????????????
    /**
     * Get, Delete???????????????
     */
    private URIBuilder uriBuilder;
    /**
     * ????????????
     */
    private LayeredConnectionSocketFactory socketFactory;
    /**
     * ??????HttpClient
     */
    private HttpClientBuilder clientBuilder;
    private CloseableHttpClient httpClient;
    /**
     * cookie?????????
     */
    private CookieStore cookieStore;
    /**
     * ?????????????????????
     */
    private Builder config;
    /**
     * ?????????https??????
     */
    private boolean isHttps;
    /**
     * ????????????1-post, 2-get, 3-put, 4-delete, 5-patch
     */
    private int type;

    /**
     * Json?????????
     */
    private static ObjectMapper mapper = new ObjectMapper();

    static {
        // ????????????NULL???????????????
//        objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        // ????????????????????????????????????????????????ISO-8601??????
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"));
        // ????????????????????????????????????
        mapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        // ?????????????????????????????????json?????????????????????
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // ???????????????
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        // ??????????????????????????????????????????????????????NULL
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        // ??????????????????????????????????????????????????????
        SimpleModule deserializeModule = new SimpleModule("DeserializeModule", new Version(1, 0, 0, null));
        mapper.registerModule(deserializeModule);
    }

    private HttpUtils(HttpRequestBase request) {
        this.request = request;

        this.clientBuilder = HttpClientBuilder.create();
        this.isHttps = request.getURI().getScheme().equalsIgnoreCase("https");
        this.config = RequestConfig.custom().setCookieSpec(CookieSpecs.BROWSER_COMPATIBILITY);
        this.cookieStore = new BasicCookieStore();

        if (request instanceof HttpPost) {
            this.type = 1;
            // this.builder = EntityBuilder.create().setParameters(new ArrayList<NameValuePair>());
            this.uriBuilder = new URIBuilder(request.getURI());

        } else if (request instanceof HttpGet) {
            this.type = 2;
            this.uriBuilder = new URIBuilder(request.getURI());

        } else if (request instanceof HttpPut) {
            this.type = 3;
            this.builder = EntityBuilder.create().setParameters(new ArrayList<NameValuePair>());

        } else if (request instanceof HttpDelete) {
            this.type = 4;
            this.uriBuilder = new URIBuilder(request.getURI());
        } else if (request instanceof HttpPatch) {
            this.type = 5;
            this.builder = EntityBuilder.create().setParameters(new ArrayList<NameValuePair>());
        }
    }

    private HttpUtils(HttpRequestBase request, HttpUtils clientUtils) {
        this(request);
        this.httpClient = clientUtils.httpClient;
        this.config = clientUtils.config;
        this.setHeaders(clientUtils.getAllHeaders());
        this.setCookieStore(clientUtils.cookieStore);
    }

    /**
     * ??????
     *
     * @param request request
     * @return HttpUtils
     */
    private static HttpUtils create(HttpRequestBase request) {
        return new HttpUtils(request);
    }

    /**
     * ??????
     *
     * @param request     request
     * @param clientUtils clientUtils
     * @return HttpUtils
     */
    private static HttpUtils create(HttpRequestBase request, HttpUtils clientUtils) {
        return new HttpUtils(request, clientUtils);
    }

    /**
     * ??????post??????
     *
     * @param url ????????????
     * @return HttpUtils
     */
    public static HttpUtils post(String url) {
        return create(new HttpPost(url));
    }

    /**
     * ??????get??????
     *
     * @param url ????????????
     * @return HttpUtils
     */
    public static HttpUtils get(String url) {
        return create(new HttpGet(url));
    }

    /**
     * ??????put??????
     *
     * @param url ????????????
     * @return HttpUtils
     */
    public static HttpUtils put(String url) {
        return create(new HttpPut(url));
    }

    /**
     * ??????delete??????
     *
     * @param url ????????????
     * @return HttpUtils
     */
    public static HttpUtils delete(String url) {
        return create(new HttpDelete(url));
    }

    /**
     * ??????patch??????
     *
     * @param url ????????????
     * @return HttpUtils
     */
    public static HttpUtils patch(String url) {
        return create(new HttpPatch(url));
    }

    /**
     * ??????post??????
     *
     * @param uri ????????????
     * @return HttpUtils
     */
    public static HttpUtils post(URI uri) {
        return create(new HttpPost(uri));
    }

    /**
     * ??????get??????
     *
     * @param uri ????????????
     * @return HttpUtils
     */
    public static HttpUtils get(URI uri) {
        return create(new HttpGet(uri));
    }

    /**
     * ??????put??????
     *
     * @param uri ????????????
     * @return HttpUtils
     */
    public static HttpUtils put(URI uri) {
        return create(new HttpPut(uri));
    }

    /**
     * ??????delete??????
     *
     * @param uri ????????????
     * @return HttpUtils
     */
    public static HttpUtils delete(URI uri) {
        return create(new HttpDelete(uri));
    }

    /**
     * ??????patch??????
     *
     * @param uri ????????????
     * @return HttpUtils
     */
    public static HttpUtils patch(URI uri) {
        return create(new HttpPatch(uri));
    }

    /**
     * ??????post??????
     *
     * @param url         ????????????
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils post(String url, HttpUtils clientUtils) {
        return create(new HttpPost(url), clientUtils);
    }

    /**
     * ??????get??????
     *
     * @param url         ????????????
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils get(String url, HttpUtils clientUtils) {
        return create(new HttpGet(url), clientUtils);
    }

    /**
     * ??????put??????
     *
     * @param url         ????????????
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils put(String url, HttpUtils clientUtils) {
        return create(new HttpPut(url), clientUtils);
    }

    /**
     * ??????delete??????
     *
     * @param url         ????????????
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils delete(String url, HttpUtils clientUtils) {
        return create(new HttpDelete(url), clientUtils);
    }

    /**
     * ??????patch??????
     *
     * @param url         ????????????
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils patch(String url, HttpUtils clientUtils) {
        return create(new HttpPatch(url), clientUtils);
    }

    /**
     * ??????post??????
     *
     * @param uri         ????????????
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils post(URI uri, HttpUtils clientUtils) {
        return create(new HttpPost(uri), clientUtils);
    }

    /**
     * ??????get??????
     *
     * @param uri         ????????????
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils get(URI uri, HttpUtils clientUtils) {
        return create(new HttpGet(uri), clientUtils);
    }

    /**
     * ??????put??????
     *
     * @param uri         ????????????
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils put(URI uri, HttpUtils clientUtils) {
        return create(new HttpPut(uri), clientUtils);
    }

    /**
     * ??????delete??????
     *
     * @param uri         ????????????
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils delete(URI uri, HttpUtils clientUtils) {
        return create(new HttpDelete(uri), clientUtils);
    }

    /**
     * ??????patch??????
     *
     * @param uri         ????????????
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils patch(URI uri, HttpUtils clientUtils) {
        return create(new HttpPatch(uri), clientUtils);
    }

    /**
     * ??????????????????
     *
     * @param name  ?????????
     * @param value ?????????
     * @return HttpUtils
     */
    public HttpUtils addParameter(final String name, final String value) {
        if (builder != null) {
            builder.getParameters().add(new BasicNameValuePair(name, value));
        } else {
            uriBuilder.addParameter(name, value);
        }
        return this;
    }

    /**
     * ??????????????????
     *
     * @param parameters ??????
     * @return HttpUtils
     */
    public HttpUtils addParameters(final NameValuePair... parameters) {
        if (builder != null) {
            builder.getParameters().addAll(Arrays.asList(parameters));
        } else {
            uriBuilder.addParameters(Arrays.asList(parameters));
        }
        return this;
    }

    /**
     * ??????????????????,????????????????????????
     *
     * @param parameters ??????
     * @return HttpUtils
     */
    public HttpUtils setParameters(final NameValuePair... parameters) {
        if (builder != null) {
            builder.setParameters(parameters);
        } else {
            uriBuilder.setParameters(Arrays.asList(parameters));
        }
        return this;
    }

    /**
     * ??????????????????,????????????????????????
     *
     * @param parameters ??????
     * @return HttpUtils
     */
    public HttpUtils setParameters(final Map<String, Object> parameters) {
        List<NameValuePair> values = new ArrayList<>();

        for (Entry<String, Object> parameter : parameters.entrySet()) {
            if (parameter.getValue() instanceof Collection) {
                for (Object value : (Collection) parameter.getValue()) {
                    values.add(new BasicNameValuePair(parameter.getKey(), String.valueOf(value)));
                }
            } else {
                values.add(new BasicNameValuePair(parameter.getKey(), String.valueOf(parameter.getValue())));
            }
        }

        if (builder != null) {
            builder.setParameters(values);
        } else {
            uriBuilder.setParameters(values);
        }
        return this;
    }

    /**
     * ??????????????????,????????????????????????
     *
     * @param file ??????
     * @return HttpUtils
     */
    public HttpUtils setParameter(final File file) {
        if (builder != null) {
            builder.setFile(file);
        } else {
            throw new UnsupportedOperationException();
        }
        return this;
    }

    /**
     * ??????????????????,????????????????????????
     *
     * @param binary ????????????
     * @return HttpUtils
     */
    public HttpUtils setParameter(final byte[] binary) {
        if (builder != null) {
            builder.setBinary(binary);
        } else {
            throw new UnsupportedOperationException();
        }
        return this;
    }

    /**
     * ??????????????????,????????????????????????
     *
     * @param serializable ??????????????????
     * @return HttpUtils
     */
    public HttpUtils setParameter(final Serializable serializable) {
        if (builder != null) {
            builder.setSerializable(serializable);
        } else {
            throw new UnsupportedOperationException();
        }
        return this;
    }

    /**
     * ???????????????Json??????
     *
     * @param parameter ????????????
     * @return HttpUtils
     */
    public HttpUtils setParameterJson(final Object parameter) {
        if (builder != null) {
            try {
                builder.setContentType(ContentType.APPLICATION_JSON);
                builder.setText(mapper.writeValueAsString(parameter));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
        return this;
    }

    /**
     * ??????????????????,????????????????????????
     *
     * @param stream ?????????
     * @return HttpUtils
     */
    public HttpUtils setParameter(final InputStream stream) {
        if (builder != null) {
            builder.setStream(stream);
        } else {
            throw new UnsupportedOperationException();
        }
        return this;
    }

    /**
     * ??????????????????,????????????????????????
     *
     * @param text ??????
     * @return HttpUtils
     */
    public HttpUtils setParameter(final String text) {
        if (builder != null) {
            builder.setText(text);
        } else {
            uriBuilder.setParameters(URLEncodedUtils.parse(text, Consts.UTF_8));
        }
        return this;
    }

    /**
     * ??????????????????
     *
     * @param encoding ?????????
     * @return HttpUtils
     */
    public HttpUtils setContentEncoding(final String encoding) {
        if (builder != null) {
            builder.setContentEncoding(encoding);
        }
        return this;
    }

    /**
     * ??????ContentType
     *
     * @param contentType ContentType
     * @return HttpUtils
     */
    public HttpUtils setContentType(ContentType contentType) {
        if (builder != null) {
            builder.setContentType(contentType);
        }
        return this;
    }

    /**
     * ??????ContentType
     *
     * @param mimeType MIME type
     * @param charset  ????????????
     * @return HttpUtils
     */
    public HttpUtils setContentType(final String mimeType, final Charset charset) {
        if (builder != null) {
            builder.setContentType(ContentType.create(mimeType, charset));
        }
        return this;
    }

    /**
     * ????????????
     *
     * @param parameters ??????Map
     * @return HttpUtils
     */
    public HttpUtils addParameters(Map<String, String> parameters) {
        List<NameValuePair> values = new ArrayList<>(parameters.size());

        for (Entry<String, String> parameter : parameters.entrySet()) {
            values.add(new BasicNameValuePair(parameter.getKey(), parameter.getValue()));
        }

        if (builder != null) {
            builder.getParameters().addAll(values);
        } else {
            uriBuilder.addParameters(values);
        }
        return this;
    }

    /**
     * ??????Header
     *
     * @param name  Header Name
     * @param value Header Value
     * @return HttpUtils
     */
    public HttpUtils addHeader(String name, String value) {
        request.addHeader(name, value);
        return this;
    }

    /**
     * ??????Header
     *
     * @param headers Header Map
     * @return HttpUtils
     */
    public HttpUtils addHeaders(Map<String, String> headers) {
        for (Entry<String, String> header : headers.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }

        return this;
    }

    /**
     * ??????Header,????????????????????????Header
     *
     * @param headers Header Map
     * @return HttpUtils
     */
    public HttpUtils setHeaders(Map<String, String> headers) {
        Header[] headerArray = new Header[headers.size()];
        int i = 0;

        for (Entry<String, String> header : headers.entrySet()) {
            headerArray[i++] = new BasicHeader(header.getKey(), header.getValue());
        }

        request.setHeaders(headerArray);
        return this;
    }

    /**
     * ??????Header,????????????????????????Header
     *
     * @param headers Header??????
     * @return HttpUtils
     */
    public HttpUtils setHeaders(Header[] headers) {
        request.setHeaders(headers);
        return this;
    }

    /**
     * ????????????Header
     *
     * @return Header??????
     */
    public Header[] getAllHeaders() {
        return request.getAllHeaders();
    }

    /**
     * ????????????name???Header??????
     *
     * @param name Header Name
     * @return HttpUtils
     */
    public HttpUtils removeHeaders(String name) {
        request.removeHeaders(name);
        return this;
    }

    /**
     * ???????????????Header
     *
     * @param header Header
     * @return HttpUtils
     */
    public HttpUtils removeHeader(Header header) {
        request.removeHeader(header);
        return this;
    }

    /**
     * ???????????????Header
     *
     * @param name  Header Name
     * @param value Header Value
     * @return HttpUtils
     */
    public HttpUtils removeHeader(String name, String value) {
        request.removeHeader(new BasicHeader(name, value));
        return this;
    }

    /**
     * ??????????????????name???Header
     *
     * @param name Header Name
     * @return ????????????true, ????????????false
     */
    public boolean containsHeader(String name) {
        return request.containsHeader(name);
    }

    /**
     * ??????Header????????????
     *
     * @return Header????????????
     */
    public HeaderIterator headerIterator() {
        return request.headerIterator();
    }

    /**
     * ????????????????????????
     *
     * @return ??????????????????
     */
    public ProtocolVersion getProtocolVersion() {
        return request.getProtocolVersion();
    }

    /**
     * ????????????Url
     *
     * @return ??????Url
     */
    public URI getURI() {
        return request.getURI();
    }

    /**
     * ????????????Url
     *
     * @param uri ??????Url
     * @return HttpUtils
     */
    public HttpUtils setURI(URI uri) {
        request.setURI(uri);
        return this;
    }

    /**
     * ????????????Url
     *
     * @param uri ??????Url
     * @return HttpUtils
     */
    public HttpUtils setURI(String uri) {
        return setURI(URI.create(uri));
    }

    /**
     * ????????????CookieStore
     *
     * @param cookieStore Cookie Store
     * @return HttpUtils
     */
    public HttpUtils setCookieStore(CookieStore cookieStore) {
        if (cookieStore == null) {
            return this;
        }
        this.cookieStore = cookieStore;
        return this;
    }

    /**
     * ??????Cookie
     *
     * @param cookies Cookie
     * @return HttpUtils
     */
    public HttpUtils addCookie(Cookie... cookies) {
        if (cookies == null) {
            return this;
        }

        for (int i = 0; i < cookies.length; i++) {
            cookieStore.addCookie(cookies[i]);
        }
        return this;
    }

    /**
     * ??????????????????
     *
     * @param hostname the hostname (IP or DNS name)
     * @param port     the port number.
     * @return HttpUtils
     */
    public HttpUtils setProxy(String hostname, int port) {
        HttpHost proxy = new HttpHost(hostname, port);
        return setProxy(proxy);
    }

    /**
     * ??????????????????
     *
     * @param hostname the hostname (IP or DNS name)
     * @param port     the port number.
     * @param schema   the name of the scheme. {@code null} indicates the default scheme "http"
     * @return HttpUtils
     */
    public HttpUtils setProxy(String hostname, int port, String schema) {
        HttpHost proxy = new HttpHost(hostname, port, schema);
        return setProxy(proxy);
    }

    /**
     * ??????????????????
     *
     * @param address the inet address
     * @return HttpUtils
     */
    public HttpUtils setProxy(InetAddress address) {
        HttpHost proxy = new HttpHost(address);
        return setProxy(proxy);
    }

    /**
     * ??????????????????
     *
     * @param host an HTTP connection to a host
     * @return HttpUtils
     */
    public HttpUtils setProxy(HttpHost host) {
        DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(host);
        clientBuilder.setRoutePlanner(routePlanner);
        return this;
    }

    /**
     * ?????????????????????JKS
     *
     * @param jksFilePath jks????????????
     * @param password    ??????
     * @return HttpUtils
     */
    public HttpUtils setJKS(String jksFilePath, String password) {
        return setJKS(new File(jksFilePath), password);
    }

    /**
     * ?????????????????????JKS
     *
     * @param jksFile  jks??????
     * @param password ??????
     * @return HttpUtils
     */
    public HttpUtils setJKS(File jksFile, String password) {
        try (InputStream inStream = new FileInputStream(jksFile)) {
            return setJKS(inStream, password);
        } catch (Exception e) {
            // LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * ?????????????????????JKS, ????????????InputStream
     *
     * @param inStream jks???
     * @param password ??????
     * @return HttpUtils
     */
    public HttpUtils setJKS(InputStream inStream, String password) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(inStream, password.toCharArray());
            return setJKS(keyStore);
        } catch (Exception e) {
            // LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * ?????????????????????JKS
     *
     * @param keyStore jks
     * @return HttpUtils
     */
    public HttpUtils setJKS(KeyStore keyStore) {
        try {
            SSLContext sslContext = SSLContexts.custom().useTLS().loadTrustMaterial(keyStore).build();
            socketFactory = new SSLConnectionSocketFactory(sslContext);
        } catch (Exception e) {
            // LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return this;
    }

    /**
     * ??????Socket????????????,??????:ms
     *
     * @param socketTimeout Socket????????????
     * @return HttpUtils
     */
    public HttpUtils setSocketTimeout(int socketTimeout) {
        config.setSocketTimeout(socketTimeout);
        return this;
    }

    /**
     * ????????????????????????,??????:ms
     *
     * @param connectTimeout ??????????????????
     * @return HttpUtils
     */
    public HttpUtils setConnectTimeout(int connectTimeout) {
        config.setConnectTimeout(connectTimeout);
        return this;
    }

    /**
     * ????????????????????????,??????:ms
     *
     * @param connectionRequestTimeout ??????????????????
     * @return HttpUtils
     */
    public HttpUtils setConnectionRequestTimeout(int connectionRequestTimeout) {
        config.setConnectionRequestTimeout(connectionRequestTimeout);
        return this;
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param circularRedirectsAllowed ????????????????????????????????????
     * @return HttpUtils
     */
    public HttpUtils setCircularRedirectsAllowed(boolean circularRedirectsAllowed) {
        config.setCircularRedirectsAllowed(circularRedirectsAllowed);
        return this;
    }

    /**
     * ????????????????????????
     *
     * @param redirectsEnabled ??????????????????
     * @return HttpUtils
     */
    public HttpUtils setRedirectsEnabled(boolean redirectsEnabled) {
        config.setRedirectsEnabled(redirectsEnabled);
        return this;
    }

    /**
     * ????????????????????????
     *
     * @param maxRedirects ??????????????????
     * @return HttpUtils
     */
    public HttpUtils maxRedirects(int maxRedirects) {
        config.setMaxRedirects(maxRedirects);
        return this;
    }

    /**
     * ????????????
     *
     * @return HTTP??????
     */
    public ResponseWrap execute() {
        settingRequest();
        if (httpClient == null) {
            httpClient = clientBuilder.build();
        }

        try {
            HttpClientContext context = HttpClientContext.create();
            CloseableHttpResponse response = httpClient.execute(request, context);
            if (builder != null) {
                String param = "";
                if (builder.getText() != null) {
                    param = builder.getText();
                } else if (builder.getParameters() != null) {
                    param = builder.getParameters().toString();
                } else if (builder.getStream() != null) {
                    param = "is stream";
                } else if (builder.getBinary() != null) {
                    param = "is binary";
                } else if (builder.getSerializable() != null) {
                    param = "is serializable";
                } else if (builder.getFile() != null) {
                    param = "is file";
                }
                // LOGGER.info("url: {} , method: {}, param: {}, execute time: {}(ms)",
                //         request.getURI(), request.getMethod(), param, stopWatch.getTime(TimeUnit.MILLISECONDS));
            } else {
                // LOGGER.info("url: {} , method: {}, execute time: {}(ms)",
                //         request.getURI(), request.getMethod(), stopWatch.getTime(TimeUnit.MILLISECONDS));
            }
            ResponseWrap responseWrap = new ResponseWrap(httpClient, request, response, context, mapper, false);
            if (!responseWrap.isSuccess()) {
                // LOGGER.error("{} ({}) response status [{}] is not 200",
                //         request.getURI(), request.getMethod(), responseWrap.getStatusLine().getStatusCode());
            }
            return responseWrap;
        } catch (IOException e) {
            // LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * ????????????
     *
     * @param responseHandler ??????????????????
     * @param <T>             ?????????
     * @return HTTP??????
     */
    public <T> T execute(final ResponseHandler<? extends T> responseHandler) {
        settingRequest();
        if (httpClient == null) {
            httpClient = clientBuilder.build();
        }

        try {
            long start = System.currentTimeMillis();
            final T result = httpClient.execute(request, responseHandler);
            long end = System.currentTimeMillis();

            // LOGGER.info("url: {} , method: {}, execute time: {}(ms)",
            //         request.getURI(), request.getMethod(), stopWatch.getTime(TimeUnit.MILLISECONDS));
            return result;
        } catch (IOException e) {
            // LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    /**
     * ????????????
     */
    @SuppressWarnings("deprecation")
    public void shutdown() {
        httpClient.getConnectionManager().shutdown();
    }

    /**
     * ??????LayeredConnectionSocketFactory ??????ssl????????????
     *
     * @return LayeredConnectionSocketFactory
     */
    private LayeredConnectionSocketFactory getSSLSocketFactory() {
        try {
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                // ????????????
                @Override
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            }).build();

            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            return sslsf;
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            // LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    /**
     * ????????????
     */
    private void settingRequest() {
        URI uri = null;
        if (uriBuilder != null && uriBuilder.getQueryParams().size() != 0) {
            try {
                uri = uriBuilder.build();
            } catch (URISyntaxException e) {
                // LOGGER.warn(e.getMessage(), e);
            }
        }

        HttpEntity httpEntity = null;

        switch (type) {
            // case 1:
                // httpEntity = builder.build();
                // uriBuilder.build();
                // if (httpEntity.getContentLength() > 0) {
                //     ((HttpPost) request).setEntity(builder.build());
                // }
                // break;

            case 1:
                HttpPost post = ((HttpPost) request);
                if (uri != null) {
                    post.setURI(uri);
                }
                break;

            case 3:
                httpEntity = builder.build();
                if (httpEntity.getContentLength() > 0) {
                    ((HttpPut) request).setEntity(httpEntity);
                }
                break;

            case 4:
                HttpDelete delete = ((HttpDelete) request);
                if (uri != null) {
                    delete.setURI(uri);
                }
                break;

            case 5:
                httpEntity = builder.build();
                if (httpEntity.getContentLength() > 0) {
                    ((HttpPatch) request).setEntity(httpEntity);
                }
                break;

            default:
                break;
        }

        if (isHttps && socketFactory != null) {
            clientBuilder.setSSLSocketFactory(socketFactory);

        } else if (isHttps) {
            clientBuilder.setSSLSocketFactory(getSSLSocketFactory());
        }

        clientBuilder.setDefaultCookieStore(cookieStore);
        request.setConfig(config.build());
    }

    /**
     * ???????????????
     */
    public class ResponseWrap {

        private CloseableHttpResponse response;
        private CloseableHttpClient httpClient;
        private HttpEntity entity;
        private HttpRequestBase request;
        private HttpClientContext context;

        private Boolean socketTimeOut;

        public ResponseWrap(CloseableHttpClient httpClient, HttpRequestBase request, CloseableHttpResponse response,
                            HttpClientContext context, ObjectMapper mapper, Boolean socketTimeOut) {
            this.response = response;
            this.httpClient = httpClient;
            this.request = request;
            this.context = context;
            this.socketTimeOut = socketTimeOut;
            HttpUtils.mapper = mapper;

            try {
                if (response.getEntity() != null) {
                    this.entity = new BufferedHttpEntity(response.getEntity());
                } else {
                    this.entity = new BasicHttpEntity();
                }

                EntityUtils.consumeQuietly(entity);
                this.response.close();
            } catch (SocketTimeoutException e) {
                this.socketTimeOut = true;
                // LOGGER.warn(e.getMessage());
            } catch (IOException e) {
                // LOGGER.warn(e.getMessage());
            }
        }

        /**
         * ????????????
         *
         * @return Boolean
         */
        public Boolean getIsSocketTimeOut() {
            return socketTimeOut;
        }

        /**
         * ????????????
         */
        public void abort() {
            request.abort();
        }

        /**
         * ????????????????????????
         *
         * @return ??????????????????
         */
        public List<URI> getRedirectLocations() {
            return context.getRedirectLocations();
        }

        /**
         * ????????????
         */
        @SuppressWarnings("deprecation")
        public void shutdown() {
            httpClient.getConnectionManager().shutdown();
        }

        /**
         * ?????????????????????200
         *
         * @return 200??????true???????????????false
         */
        public boolean isSuccess() {
            return getStatusLine().getStatusCode() == HttpStatus.SC_OK;
        }

        public String getRequestUrl() {
            return request.getURI().toString();
        }

        /**
         * ?????????????????????String,??????????????? "UTF-8"
         *
         * @return ????????????
         */
        public String getString() {
            return getString(Consts.UTF_8);
        }

        /**
         * ?????????????????????String
         *
         * @param defaultCharset ????????????
         * @return ????????????
         */
        public String getString(Charset defaultCharset) {
            try {
                return EntityUtils.toString(entity, defaultCharset);
            } catch (ParseException | IOException e) {
                // LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        /**
         * ?????????????????????
         *
         * @return ???????????????
         */
        public Header getContentType() {
            return entity.getContentType();
        }

        /**
         * ??????????????????,?????????????????????
         *
         * @return ?????????
         */
        public Charset getCharset() {
            ContentType contentType = ContentType.get(entity);
            if (contentType == null) {
                return null;
            }
            return contentType.getCharset();
        }

        /**
         * ?????????????????????????????????
         *
         * @return ????????????
         */
        public byte[] getByteArray() {
            try {
                return EntityUtils.toByteArray(entity);
            } catch (ParseException | IOException e) {
                // LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        /**
         * ????????????Header
         *
         * @return ??????Header
         */
        public Header[] getAllHeaders() {
            return response.getAllHeaders();
        }

        /**
         * ?????????????????????Header??????
         *
         * @param name Header Name
         * @return ???????????????Header??????
         */
        public Header[] getHeaders(String name) {
            return response.getHeaders(name);
        }

        /**
         * ????????????????????????
         *
         * @return ??????????????????
         */
        public StatusLine getStatusLine() {
            return response.getStatusLine();
        }

        /**
         * ????????????name???Header??????
         *
         * @param name Header Name
         */
        public void removeHeaders(String name) {
            response.removeHeaders(name);
        }

        /**
         * ???????????????Header
         *
         * @param header Header
         */
        public void removeHeader(Header header) {
            response.removeHeader(header);
        }

        /**
         * ???????????????Header
         *
         * @param name  Header Name
         * @param value Header Value
         */
        public void removeHeader(String name, String value) {
            response.removeHeader(new BasicHeader(name, value));
        }

        /**
         * ??????????????????name???Header
         *
         * @param name Header Name
         * @return ????????????true, ????????????false
         */
        public boolean containsHeader(String name) {
            return response.containsHeader(name);
        }

        /**
         * ??????Header????????????
         *
         * @return Header????????????
         */
        public HeaderIterator headerIterator() {
            return response.headerIterator();
        }

        /**
         * ????????????????????????
         *
         * @return ??????????????????
         */
        public ProtocolVersion getProtocolVersion() {
            return response.getProtocolVersion();
        }

        /**
         * ??????CookieStore
         *
         * @return CookieStore
         */
        public CookieStore getCookieStore() {
            return context.getCookieStore();
        }

        /**
         * ??????Cookie??????
         *
         * @return Cookie??????
         */
        public List<Cookie> getCookies() {
            return getCookieStore().getCookies();
        }

        /**
         * ??????InputStream,?????????????????????
         *
         * @return InputStream
         */
        public InputStream getInputStream() {
            try {
                return entity.getContent();
            } catch (IllegalStateException | IOException e) {
                // LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        /**
         * ??????BufferedReader
         *
         * @return BufferedReader
         */
        public BufferedReader getBufferedReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), getCharset()));
        }

        /**
         * ???????????????????????????
         *
         * @param filePth ??????
         */
        public void transferTo(String filePth) {
            transferTo(new File(filePth));
        }

        /**
         * ???????????????????????????
         *
         * @param file ??????
         */
        public void transferTo(File file) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                transferTo(fileOutputStream);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        /**
         * ?????????OutputStream,???????????????OutputStream
         *
         * @param outputStream OutputStream
         */
        public void transferTo(OutputStream outputStream) {
            try {
                entity.writeTo(outputStream);
            } catch (Exception e) {
                // LOGGER.warn(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        public String getResponseStr() {
            try {
                return EntityUtils.toString(entity);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        /**
         * ??????Json???????????????
         *
         * @param clazz ???
         * @param <T>   ??????
         * @return Json???????????????
         */
        public <T> T getJsonObject(Class<T> clazz) {
            if (isSuccess()) {
                try {
                    return mapper.readValue(getByteArray(), clazz);
                } catch (IOException e) {
                    // LOGGER.warn(e.getMessage(), e);
                    throw new RuntimeException(e.getMessage(), e);
                }
            } else {
                String msg = String.format("{} ({}) response status [{}] is not 200",
                        request.getURI(), request.getMethod(), getStatusLine().getStatusCode());
                // LOGGER.warn(msg);
                throw new RuntimeException(msg);
            }
        }

        /**
         * ??????Json?????????????????????
         *
         * @param typeRef TypeReference
         * @param <T>     ??????
         * @return Json?????????????????????
         */
        public <T> T getJsonObject(TypeReference<T> typeRef) {
            if (isSuccess()) {
                try {
                    return mapper.readValue(getByteArray(), typeRef);
                } catch (IOException e) {
                    // LOGGER.warn(e.getMessage(), e);
                    throw new RuntimeException(e.getMessage(), e);
                }
            } else {
                String msg = String.format("{} ({}) response status [{}] is not 200",
                        request.getURI(), request.getMethod(), getStatusLine().getStatusCode());
                // LOGGER.warn(msg);
                throw new RuntimeException(msg);
            }
        }
    }
}
