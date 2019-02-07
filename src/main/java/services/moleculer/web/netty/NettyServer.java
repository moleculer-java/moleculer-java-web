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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import services.moleculer.ServiceBroker;
import services.moleculer.service.Service;
import services.moleculer.web.ApiGateway;

public class NettyServer extends Service {

	// --- GATEWAY SERVICE ---
	
	protected ApiGateway gateway;
	
	// --- INTERNAL VARIABLES ---

	protected int port = 3000;

	protected String address;

	protected EventLoopGroup eventLoopGroup;

	protected ChannelHandler handler;
	
	protected ScheduledExecutorService scheduler;

	protected int tries;
	
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
	
	// --- START NETTY SERVER ---

	@Override
	public void started(ServiceBroker broker) throws Exception {
		super.started(broker);

		// Worker group
		if (eventLoopGroup == null) {
			eventLoopGroup = new NioEventLoopGroup(3,
					new ThreadPerTaskExecutor(new DefaultThreadFactory(NettyServer.class, Thread.MAX_PRIORITY - 1)));
		}

		// Find ApiGateway
		scheduler = broker.getConfig().getScheduler();
		scheduler.execute(this::initApiGateway);			
		
		// Create request chain
		ServerBootstrap bootstrap = new ServerBootstrap();
		bootstrap.group(eventLoopGroup);
		bootstrap.channel(NioServerSocketChannel.class);

		// Define request chain
		if (handler == null) {
			handler = new ChannelInitializer<Channel>() {

				@Override
				protected void initChannel(Channel ch) throws Exception {
					ChannelPipeline p = ch.pipeline();
					if (useSSL) {
						p.addLast(createSslHandler(ch));
					}
					p.addLast(new HttpRequestDecoder());
					p.addLast(new MoleculerHandler(gateway, broker));
				}

			};
		}

		// Set child handler
		bootstrap.childHandler(handler);

		// Start server
		if (address == null) {
			bootstrap.bind(port).get();
		} else {
			bootstrap.bind(address, port).get();
		}
	}

	// --- INIT GATEWAY ---
	
	protected void initApiGateway() {
		gateway = getService(broker, ApiGateway.class);
		if (gateway == null) {
			tries++;
			if (tries < 50) {
				scheduler.schedule(this::initApiGateway, 200, TimeUnit.MILLISECONDS);
			} else {
				logger.error("ApiGateway Service not defined!");
			}
		} else {
			logger.info("ApiGateway connected to Netty Server.");
		}
	}
	
	// --- STOP NETTY SERVER ---

	@Override
	public void stopped() {
		super.stopped();
		if (eventLoopGroup != null) {
			eventLoopGroup.shutdownGracefully();
			eventLoopGroup = null;
		}
		handler = null;
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
					}

					@Override
					public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
							throws CertificateException {
					}

					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
					}

				} };
				builder.trustManager(new SimpleTrustManagerFactory() {

					@Override
					protected void engineInit(KeyStore keyStore) throws Exception {
					}

					@Override
					protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception {
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
	
}