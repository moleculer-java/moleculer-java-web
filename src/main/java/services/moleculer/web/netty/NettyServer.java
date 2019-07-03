/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2018 Andras Berkes [andras.berkes@programmer.net]<br>
 * Based on Moleculer Framework for NodeJS [https://moleculer.services].
 * <br><br>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:<br>
 * <br>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.<br>
 * <br>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package services.moleculer.web.netty;

import static services.moleculer.web.common.GatewayUtils.getFileURL;
import static services.moleculer.web.common.GatewayUtils.getService;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslServerContext;
import io.netty.handler.ssl.OpenSslServerSessionContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import services.moleculer.ServiceBroker;
import services.moleculer.eventbus.Listener;
import services.moleculer.eventbus.Subscribe;
import services.moleculer.service.Service;
import services.moleculer.web.ApiGateway;

public class NettyServer extends Service {

	// --- GATEWAY SERVICE ---

	protected ApiGateway gateway;

	// --- INTERNAL VARIABLES ---

	protected int port = 3000;

	protected String address;

	protected EventLoopGroup singletonGroup;

	protected ChannelHandler handler;

	protected int webSocketCleanupSeconds = 15;

	// --- SSL PROPERTIES ---

	protected boolean useSSL;

	protected TrustManagerFactory trustManagerFactory;

	// --- JDK SSL PROPERTIES ---

	protected String keyStoreFilePath;

	protected String keyStorePassword;

	protected String keyStoreType = "jks";

	// --- OPENSSL PROPERTIES ---

	protected String keyCertChainFilePath;

	protected String keyFilePath;

	protected boolean openSslSessionCacheEnabled = true;

	protected SslContext cachedSslContext;

	// --- WEBSOCKET REGISTRY ---

	protected NettyWebSocketRegistry webSocketRegistry;

	// --- INIT GATEWAY ---

	@Subscribe("$services.changed")
	public Listener evt = payload -> {
		if (gateway == null) {
			boolean localService = payload.get("localService", false);
			if (localService) {
				gateway = getService(broker, ApiGateway.class);
				if (gateway != null) {
					if (webSocketRegistry == null) {
						webSocketRegistry = new NettyWebSocketRegistry(broker, webSocketCleanupSeconds);
					}
					gateway.setWebSocketRegistry(webSocketRegistry);
					logger.info("ApiGateway connected to Netty Server.");
				}
			}
		}
	};

	// --- START NETTY SERVER ---

	@Override
	public void started(ServiceBroker broker) throws Exception {
		super.started(broker);

		// Worker group
		if (singletonGroup == null) {
			singletonGroup = new NioEventLoopGroup(1, Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "Netty Server on port " + port);
				t.setPriority(Thread.MAX_PRIORITY - 1);
				return t;
			}));
		}

		// Create request chain
		ServerBootstrap bootstrap = new ServerBootstrap();
		bootstrap.group(singletonGroup, singletonGroup);

		bootstrap.channel(NioServerSocketChannel.class);

		// Create webSocketRegistry
		if (webSocketRegistry == null) {
			webSocketRegistry = new NettyWebSocketRegistry(broker, webSocketCleanupSeconds);
		}

		// Define request chain
		if (handler == null) {
			handler = new ChannelInitializer<Channel>() {

				@Override
				protected void initChannel(Channel ch) throws Exception {
					ChannelPipeline p = ch.pipeline();
					if (useSSL) {
						p.addLast("ssl", createSslHandler(ch));
					}
					p.addLast("decoder", new HttpRequestDecoder());
					p.addLast("handler", new MoleculerHandler(gateway, broker, webSocketRegistry));
				}

			};
		}

		// Set child handler
		bootstrap.childHandler(handler);

		// Start server
		if (address == null) {
			bootstrap.bind(port).get();
			logger.info("Netty Server started at \"" + (useSSL ? "https" : "http") + "://localhost:" + port + "\".");
		} else {
			bootstrap.bind(address, port).get();
		}
	}

	// --- STOP NETTY SERVER ---

	@Override
	public void stopped() {
		super.stopped();
		if (singletonGroup != null) {
			singletonGroup.shutdownGracefully();
			singletonGroup = null;
		}
		handler = null;
		if (webSocketRegistry != null) {
			webSocketRegistry.stopped();
			webSocketRegistry = null;
		}
	}

	// --- SSL HANDLER ---

	protected SslHandler createSslHandler(Channel ch) throws Exception {
		SslContext sslContext = getSslContext();
		InetSocketAddress remoteAddress = (InetSocketAddress) ch.remoteAddress();
		SSLEngine sslEngine = sslContext.newEngine(ByteBufAllocator.DEFAULT,
				remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort());
		return new SslHandler(sslEngine);
	}

	protected synchronized SslContext getSslContext() throws Exception {
		if (cachedSslContext == null) {
			SslContextBuilder builder;
			if (keyCertChainFilePath != null || keyFilePath != null) {

				// OpenSSL
				InputStream keyCertChainInputStream = getFileURL(keyCertChainFilePath).openStream();
				InputStream keyInputStream = getFileURL(keyFilePath).openStream();
				builder = SslContextBuilder.forServer(keyCertChainInputStream, keyInputStream);

			} else {

				// JDK SSL
				KeyStore keyStore = KeyStore.getInstance(keyStoreType);
				InputStream keyStoreInputStream = getFileURL(keyStoreFilePath).openStream();
				keyStore.load(keyStoreInputStream, keyStorePassword == null ? null : keyStorePassword.toCharArray());
				KeyManagerFactory keyManagerFactory = KeyManagerFactory
						.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				keyManagerFactory.getProvider();
				keyManagerFactory.init(keyStore, keyStorePassword == null ? null : keyStorePassword.toCharArray());
				builder = SslContextBuilder.forServer(keyManagerFactory);

			}
			Collection<String> cipherSuites;
			if (keyCertChainFilePath != null || keyFilePath != null) {

				// OpenSSL
				builder.sslProvider(SslProvider.OPENSSL);
				cipherSuites = OpenSsl.availableOpenSslCipherSuites();

			} else {

				// JDK SSL
				builder.sslProvider(SslProvider.JDK);
				SSLContext context = SSLContext.getInstance("TLS");
				context.init(null, null, null);
				SSLEngine engine = context.createSSLEngine();
				cipherSuites = new ArrayList<>();
				Collections.addAll(cipherSuites, engine.getEnabledCipherSuites());

			}
			if (cipherSuites != null && cipherSuites.isEmpty()) {
				builder.ciphers(cipherSuites);
			}
			if (trustManagerFactory == null) {
				TrustManager[] mgrs = new TrustManager[] { new X509TrustManager() {

					@Override
					public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
							throws CertificateException {

						// Trust all certificates
					}

					@Override
					public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
							throws CertificateException {

						// Trust all servers
					}

					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];

						// Trust all issuers
					}

				} };
				builder.trustManager(new SimpleTrustManagerFactory() {

					@Override
					protected void engineInit(KeyStore keyStore) throws Exception {

						// Do nothing
					}

					@Override
					protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception {

						// Do nothing
					}

					@Override
					protected TrustManager[] engineGetTrustManagers() {
						return mgrs.clone();
					}

				});
			} else {
				builder.trustManager(trustManagerFactory);
			}
			cachedSslContext = builder.build();
			if (cachedSslContext instanceof OpenSslServerContext) {
				SSLSessionContext sslSessionContext = cachedSslContext.sessionContext();
				if (sslSessionContext instanceof OpenSslServerSessionContext) {
					((OpenSslServerSessionContext) sslSessionContext)
							.setSessionCacheEnabled(openSslSessionCacheEnabled);
				}
			}
		}
		return cachedSslContext;
	}

	// --- GETTERS AND SETTERS ---

	public int getWebSocketCleanupSeconds() {
		return webSocketCleanupSeconds;
	}

	public void setWebSocketCleanupSeconds(int webSocketCleanupSeconds) {
		this.webSocketCleanupSeconds = webSocketCleanupSeconds;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isUseSSL() {
		return useSSL;
	}

	public void setUseSSL(boolean useSSL) {
		this.useSSL = useSSL;
	}

	public TrustManagerFactory getTrustManagerFactory() {
		return trustManagerFactory;
	}

	public void setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
		this.trustManagerFactory = trustManagerFactory;
	}

	public String getKeyStoreFilePath() {
		return keyStoreFilePath;
	}

	public void setKeyStoreFilePath(String keyStoreFilePath) {
		this.keyStoreFilePath = keyStoreFilePath;
	}

	public String getKeyStorePassword() {
		return keyStorePassword;
	}

	public void setKeyStorePassword(String keyStorePassword) {
		this.keyStorePassword = keyStorePassword;
	}

	public String getKeyStoreType() {
		return keyStoreType;
	}

	public void setKeyStoreType(String keyStoreType) {
		this.keyStoreType = keyStoreType;
	}

	public String getKeyCertChainFilePath() {
		return keyCertChainFilePath;
	}

	public void setKeyCertChainFilePath(String keyCertChainFilePath) {
		this.keyCertChainFilePath = keyCertChainFilePath;
	}

	public String getKeyFilePath() {
		return keyFilePath;
	}

	public void setKeyFilePath(String keyFilePath) {
		this.keyFilePath = keyFilePath;
	}

	public boolean isOpenSslSessionCacheEnabled() {
		return openSslSessionCacheEnabled;
	}

	public void setOpenSslSessionCacheEnabled(boolean openSslSessionCacheEnabled) {
		this.openSslSessionCacheEnabled = openSslSessionCacheEnabled;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public EventLoopGroup getSingletonGroup() {
		return singletonGroup;
	}

	public void setSingletonGroup(EventLoopGroup singletonGroup) {
		this.singletonGroup = singletonGroup;
	}

	public ChannelHandler getHandler() {
		return handler;
	}

	public void setHandler(ChannelHandler handler) {
		this.handler = handler;
	}

}