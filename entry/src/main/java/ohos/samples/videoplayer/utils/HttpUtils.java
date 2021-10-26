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
 * HTTP 工具类
 *
 * @author linch
 * @date 2019/10/29
 * @since v1.0.0
 */
public final class HttpUtils {

    /**
     * 请求对象
     */
    private HttpRequestBase request;
    /**
     * Post, Put, Patch请求的参数
     */
    private EntityBuilder builder; // Post, Put请求的参数
    /**
     * Get, Delete请求的参数
     */
    private URIBuilder uriBuilder;
    /**
     * 连接工厂
     */
    private LayeredConnectionSocketFactory socketFactory;
    /**
     * 构建HttpClient
     */
    private HttpClientBuilder clientBuilder;
    private CloseableHttpClient httpClient;
    /**
     * cookie存储器
     */
    private CookieStore cookieStore;
    /**
     * 请求的相关配置
     */
    private Builder config;
    /**
     * 是否是https请求
     */
    private boolean isHttps;
    /**
     * 请求类型1-post, 2-get, 3-put, 4-delete, 5-patch
     */
    private int type;

    /**
     * Json转换器
     */
    private static ObjectMapper mapper = new ObjectMapper();

    static {
        // 序列化：NULL值不序列化
//        objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        // 序列化：时间类型序列化格式设定为ISO-8601格式
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"));
        // 序列化：时区设定为东八区
        mapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        // 反序列化：允许对象忽略json中不存在的属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 反序列化：
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        // 反序列化：允许将枚举未知值反序列化为NULL
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        // 反序列化：使用自定义的日期类型转换器
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
     * 创建
     *
     * @param request request
     * @return HttpUtils
     */
    private static HttpUtils create(HttpRequestBase request) {
        return new HttpUtils(request);
    }

    /**
     * 创建
     *
     * @param request     request
     * @param clientUtils clientUtils
     * @return HttpUtils
     */
    private static HttpUtils create(HttpRequestBase request, HttpUtils clientUtils) {
        return new HttpUtils(request, clientUtils);
    }

    /**
     * 创建post请求
     *
     * @param url 请求地址
     * @return HttpUtils
     */
    public static HttpUtils post(String url) {
        return create(new HttpPost(url));
    }

    /**
     * 创建get请求
     *
     * @param url 请求地址
     * @return HttpUtils
     */
    public static HttpUtils get(String url) {
        return create(new HttpGet(url));
    }

    /**
     * 创建put请求
     *
     * @param url 请求地址
     * @return HttpUtils
     */
    public static HttpUtils put(String url) {
        return create(new HttpPut(url));
    }

    /**
     * 创建delete请求
     *
     * @param url 请求地址
     * @return HttpUtils
     */
    public static HttpUtils delete(String url) {
        return create(new HttpDelete(url));
    }

    /**
     * 创建patch请求
     *
     * @param url 请求地址
     * @return HttpUtils
     */
    public static HttpUtils patch(String url) {
        return create(new HttpPatch(url));
    }

    /**
     * 创建post请求
     *
     * @param uri 请求地址
     * @return HttpUtils
     */
    public static HttpUtils post(URI uri) {
        return create(new HttpPost(uri));
    }

    /**
     * 创建get请求
     *
     * @param uri 请求地址
     * @return HttpUtils
     */
    public static HttpUtils get(URI uri) {
        return create(new HttpGet(uri));
    }

    /**
     * 创建put请求
     *
     * @param uri 请求地址
     * @return HttpUtils
     */
    public static HttpUtils put(URI uri) {
        return create(new HttpPut(uri));
    }

    /**
     * 创建delete请求
     *
     * @param uri 请求地址
     * @return HttpUtils
     */
    public static HttpUtils delete(URI uri) {
        return create(new HttpDelete(uri));
    }

    /**
     * 创建patch请求
     *
     * @param uri 请求地址
     * @return HttpUtils
     */
    public static HttpUtils patch(URI uri) {
        return create(new HttpPatch(uri));
    }

    /**
     * 创建post请求
     *
     * @param url         请求地址
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils post(String url, HttpUtils clientUtils) {
        return create(new HttpPost(url), clientUtils);
    }

    /**
     * 创建get请求
     *
     * @param url         请求地址
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils get(String url, HttpUtils clientUtils) {
        return create(new HttpGet(url), clientUtils);
    }

    /**
     * 创建put请求
     *
     * @param url         请求地址
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils put(String url, HttpUtils clientUtils) {
        return create(new HttpPut(url), clientUtils);
    }

    /**
     * 创建delete请求
     *
     * @param url         请求地址
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils delete(String url, HttpUtils clientUtils) {
        return create(new HttpDelete(url), clientUtils);
    }

    /**
     * 创建patch请求
     *
     * @param url         请求地址
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils patch(String url, HttpUtils clientUtils) {
        return create(new HttpPatch(url), clientUtils);
    }

    /**
     * 创建post请求
     *
     * @param uri         请求地址
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils post(URI uri, HttpUtils clientUtils) {
        return create(new HttpPost(uri), clientUtils);
    }

    /**
     * 创建get请求
     *
     * @param uri         请求地址
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils get(URI uri, HttpUtils clientUtils) {
        return create(new HttpGet(uri), clientUtils);
    }

    /**
     * 创建put请求
     *
     * @param uri         请求地址
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils put(URI uri, HttpUtils clientUtils) {
        return create(new HttpPut(uri), clientUtils);
    }

    /**
     * 创建delete请求
     *
     * @param uri         请求地址
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils delete(URI uri, HttpUtils clientUtils) {
        return create(new HttpDelete(uri), clientUtils);
    }

    /**
     * 创建patch请求
     *
     * @param uri         请求地址
     * @param clientUtils HttpUtils
     * @return HttpUtils
     */
    public static HttpUtils patch(URI uri, HttpUtils clientUtils) {
        return create(new HttpPatch(uri), clientUtils);
    }

    /**
     * 添加单个参数
     *
     * @param name  参数名
     * @param value 参数值
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
     * 添加多个参数
     *
     * @param parameters 参数
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
     * 设置请求参数,会覆盖之前的参数
     *
     * @param parameters 参数
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
     * 设置请求参数,会覆盖之前的参数
     *
     * @param parameters 参数
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
     * 设置请求参数,会覆盖之前的参数
     *
     * @param file 文件
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
     * 设置请求参数,会覆盖之前的参数
     *
     * @param binary 字节数组
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
     * 设置请求参数,会覆盖之前的参数
     *
     * @param serializable 可序列化对象
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
     * 设置参数为Json对象
     *
     * @param parameter 参数对象
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
     * 设置请求参数,会覆盖之前的参数
     *
     * @param stream 输入流
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
     * 设置请求参数,会覆盖之前的参数
     *
     * @param text 文本
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
     * 设置内容编码
     *
     * @param encoding 编码集
     * @return HttpUtils
     */
    public HttpUtils setContentEncoding(final String encoding) {
        if (builder != null) {
            builder.setContentEncoding(encoding);
        }
        return this;
    }

    /**
     * 设置ContentType
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
     * 设置ContentType
     *
     * @param mimeType MIME type
     * @param charset  内容编码
     * @return HttpUtils
     */
    public HttpUtils setContentType(final String mimeType, final Charset charset) {
        if (builder != null) {
            builder.setContentType(ContentType.create(mimeType, charset));
        }
        return this;
    }

    /**
     * 添加参数
     *
     * @param parameters 参数Map
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
     * 添加Header
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
     * 添加Header
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
     * 设置Header,会覆盖所有之前的Header
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
     * 设置Header,会覆盖所有之前的Header
     *
     * @param headers Header数组
     * @return HttpUtils
     */
    public HttpUtils setHeaders(Header[] headers) {
        request.setHeaders(headers);
        return this;
    }

    /**
     * 获取所有Header
     *
     * @return Header数组
     */
    public Header[] getAllHeaders() {
        return request.getAllHeaders();
    }

    /**
     * 移除指定name的Header列表
     *
     * @param name Header Name
     * @return HttpUtils
     */
    public HttpUtils removeHeaders(String name) {
        request.removeHeaders(name);
        return this;
    }

    /**
     * 移除指定的Header
     *
     * @param header Header
     * @return HttpUtils
     */
    public HttpUtils removeHeader(Header header) {
        request.removeHeader(header);
        return this;
    }

    /**
     * 移除指定的Header
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
     * 是否存在指定name的Header
     *
     * @param name Header Name
     * @return 存在返回true, 否则返回false
     */
    public boolean containsHeader(String name) {
        return request.containsHeader(name);
    }

    /**
     * 获取Header的迭代器
     *
     * @return Header的迭代器
     */
    public HeaderIterator headerIterator() {
        return request.headerIterator();
    }

    /**
     * 获取协议版本信息
     *
     * @return 协议版本信息
     */
    public ProtocolVersion getProtocolVersion() {
        return request.getProtocolVersion();
    }

    /**
     * 获取请求Url
     *
     * @return 请求Url
     */
    public URI getURI() {
        return request.getURI();
    }

    /**
     * 设置请求Url
     *
     * @param uri 请求Url
     * @return HttpUtils
     */
    public HttpUtils setURI(URI uri) {
        request.setURI(uri);
        return this;
    }

    /**
     * 设置请求Url
     *
     * @param uri 请求Url
     * @return HttpUtils
     */
    public HttpUtils setURI(String uri) {
        return setURI(URI.create(uri));
    }

    /**
     * 设置一个CookieStore
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
     * 添加Cookie
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
     * 设置网络代理
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
     * 设置网络代理
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
     * 设置网络代理
     *
     * @param address the inet address
     * @return HttpUtils
     */
    public HttpUtils setProxy(InetAddress address) {
        HttpHost proxy = new HttpHost(address);
        return setProxy(proxy);
    }

    /**
     * 设置网络代理
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
     * 设置双向认证的JKS
     *
     * @param jksFilePath jks文件路径
     * @param password    密码
     * @return HttpUtils
     */
    public HttpUtils setJKS(String jksFilePath, String password) {
        return setJKS(new File(jksFilePath), password);
    }

    /**
     * 设置双向认证的JKS
     *
     * @param jksFile  jks文件
     * @param password 密码
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
     * 设置双向认证的JKS, 不会关闭InputStream
     *
     * @param inStream jks流
     * @param password 密码
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
     * 设置双向认证的JKS
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
     * 设置Socket超时时间,单位:ms
     *
     * @param socketTimeout Socket超时时间
     * @return HttpUtils
     */
    public HttpUtils setSocketTimeout(int socketTimeout) {
        config.setSocketTimeout(socketTimeout);
        return this;
    }

    /**
     * 设置连接超时时间,单位:ms
     *
     * @param connectTimeout 连接超时时间
     * @return HttpUtils
     */
    public HttpUtils setConnectTimeout(int connectTimeout) {
        config.setConnectTimeout(connectTimeout);
        return this;
    }

    /**
     * 设置请求超时时间,单位:ms
     *
     * @param connectionRequestTimeout 请求超时时间
     * @return HttpUtils
     */
    public HttpUtils setConnectionRequestTimeout(int connectionRequestTimeout) {
        config.setConnectionRequestTimeout(connectionRequestTimeout);
        return this;
    }

    /**
     * 设置是否允许服务端循环重定向
     *
     * @param circularRedirectsAllowed 是否允许服务端循环重定向
     * @return HttpUtils
     */
    public HttpUtils setCircularRedirectsAllowed(boolean circularRedirectsAllowed) {
        config.setCircularRedirectsAllowed(circularRedirectsAllowed);
        return this;
    }

    /**
     * 设置是否启用跳转
     *
     * @param redirectsEnabled 是否启用跳转
     * @return HttpUtils
     */
    public HttpUtils setRedirectsEnabled(boolean redirectsEnabled) {
        config.setRedirectsEnabled(redirectsEnabled);
        return this;
    }

    /**
     * 设置重定向的次数
     *
     * @param maxRedirects 重定向的次数
     * @return HttpUtils
     */
    public HttpUtils maxRedirects(int maxRedirects) {
        config.setMaxRedirects(maxRedirects);
        return this;
    }

    /**
     * 执行请求
     *
     * @return HTTP应答
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
     * 执行请求
     *
     * @param responseHandler 响应处理对象
     * @param <T>             泛型类
     * @return HTTP应答
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
     * 关闭连接
     */
    @SuppressWarnings("deprecation")
    public void shutdown() {
        httpClient.getConnectionManager().shutdown();
    }

    /**
     * 获取LayeredConnectionSocketFactory 使用ssl单向认证
     *
     * @return LayeredConnectionSocketFactory
     */
    private LayeredConnectionSocketFactory getSSLSocketFactory() {
        try {
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                // 信任所有
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
     * 设置请求
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
     * 应答包装类
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
         * 是否超时
         *
         * @return Boolean
         */
        public Boolean getIsSocketTimeOut() {
            return socketTimeOut;
        }

        /**
         * 终止请求
         */
        public void abort() {
            request.abort();
        }

        /**
         * 获取重定向的地址
         *
         * @return 重定向的地址
         */
        public List<URI> getRedirectLocations() {
            return context.getRedirectLocations();
        }

        /**
         * 关闭连接
         */
        @SuppressWarnings("deprecation")
        public void shutdown() {
            httpClient.getConnectionManager().shutdown();
        }

        /**
         * 判断响应是否为200
         *
         * @return 200返回true，否则返回false
         */
        public boolean isSuccess() {
            return getStatusLine().getStatusCode() == HttpStatus.SC_OK;
        }

        public String getRequestUrl() {
            return request.getURI().toString();
        }

        /**
         * 获取响应内容为String,默认编码为 "UTF-8"
         *
         * @return 响应内容
         */
        public String getString() {
            return getString(Consts.UTF_8);
        }

        /**
         * 获取响应内容为String
         *
         * @param defaultCharset 指定编码
         * @return 响应内容
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
         * 获取响应的类型
         *
         * @return 响应的类型
         */
        public Header getContentType() {
            return entity.getContentType();
        }

        /**
         * 获取响应编码,如果是文本的话
         *
         * @return 编码集
         */
        public Charset getCharset() {
            ContentType contentType = ContentType.get(entity);
            if (contentType == null) {
                return null;
            }
            return contentType.getCharset();
        }

        /**
         * 获取响应内容为字节数组
         *
         * @return 响应内容
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
         * 获取所有Header
         *
         * @return 所有Header
         */
        public Header[] getAllHeaders() {
            return response.getAllHeaders();
        }

        /**
         * 获取指定名称的Header列表
         *
         * @param name Header Name
         * @return 指定名称的Header列表
         */
        public Header[] getHeaders(String name) {
            return response.getHeaders(name);
        }

        /**
         * 获取响应状态信息
         *
         * @return 响应状态信息
         */
        public StatusLine getStatusLine() {
            return response.getStatusLine();
        }

        /**
         * 移除指定name的Header列表
         *
         * @param name Header Name
         */
        public void removeHeaders(String name) {
            response.removeHeaders(name);
        }

        /**
         * 移除指定的Header
         *
         * @param header Header
         */
        public void removeHeader(Header header) {
            response.removeHeader(header);
        }

        /**
         * 移除指定的Header
         *
         * @param name  Header Name
         * @param value Header Value
         */
        public void removeHeader(String name, String value) {
            response.removeHeader(new BasicHeader(name, value));
        }

        /**
         * 是否存在指定name的Header
         *
         * @param name Header Name
         * @return 存在返回true, 否则返回false
         */
        public boolean containsHeader(String name) {
            return response.containsHeader(name);
        }

        /**
         * 获取Header的迭代器
         *
         * @return Header的迭代器
         */
        public HeaderIterator headerIterator() {
            return response.headerIterator();
        }

        /**
         * 获取协议版本信息
         *
         * @return 协议版本信息
         */
        public ProtocolVersion getProtocolVersion() {
            return response.getProtocolVersion();
        }

        /**
         * 获取CookieStore
         *
         * @return CookieStore
         */
        public CookieStore getCookieStore() {
            return context.getCookieStore();
        }

        /**
         * 获取Cookie列表
         *
         * @return Cookie列表
         */
        public List<Cookie> getCookies() {
            return getCookieStore().getCookies();
        }

        /**
         * 获取InputStream,需要手动关闭流
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
         * 获取BufferedReader
         *
         * @return BufferedReader
         */
        public BufferedReader getBufferedReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), getCharset()));
        }

        /**
         * 响应内容写入到文件
         *
         * @param filePth 路径
         */
        public void transferTo(String filePth) {
            transferTo(new File(filePth));
        }

        /**
         * 响应内容写入到文件
         *
         * @param file 文件
         */
        public void transferTo(File file) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                transferTo(fileOutputStream);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        /**
         * 写入到OutputStream,并不会关闭OutputStream
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
         * 获取Json对应的对象
         *
         * @param clazz 类
         * @param <T>   泛型
         * @return Json对应的对象
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
         * 获取Json对应的泛型对象
         *
         * @param typeRef TypeReference
         * @param <T>     泛型
         * @return Json对应的泛型对象
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
