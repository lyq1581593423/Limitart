package org.slingerxv.limitart.net.binary.server.config;

import java.util.HashSet;

import org.slingerxv.limitart.net.binary.codec.AbstractBinaryDecoder;
import org.slingerxv.limitart.net.binary.codec.AbstractBinaryEncoder;
import org.slingerxv.limitart.util.StringUtil;

/**
 * 二进制服务器配置
 * 
 * @author hank
 *
 */
public final class BinaryServerConfig {
	private String serverName;
	private int port;
	private String connectionPass;
	private int connectionValidateTimeInSec;
	private AbstractBinaryDecoder decoder;
	private AbstractBinaryEncoder encoder;
	private HashSet<String> whiteList;

	private BinaryServerConfig(BinaryServerConfigBuilder builder) {
		this.serverName = builder.serverName;
		this.port = builder.port;
		this.connectionPass = builder.connectionPass;
		this.connectionValidateTimeInSec = builder.connectionValidateTimeInSec;
		this.decoder = builder.decoder;
		this.encoder = builder.encoder;
		this.whiteList = builder.whiteList;
	}

	public String getServerName() {
		return this.serverName;
	}

	public int getConnectionValidateTimeInSec() {
		return connectionValidateTimeInSec;
	}

	public String getConnectionPass() {
		return connectionPass;
	}

	public int getPort() {
		return port;
	}

	public AbstractBinaryDecoder getDecoder() {
		return decoder;
	}

	public AbstractBinaryEncoder getEncoder() {
		return encoder;
	}

	public HashSet<String> getWhiteList() {
		return whiteList;
	}
	public static class BinaryServerConfigBuilder {
		private String serverName;
		private int port;
		private String connectionPass;
		private int connectionValidateTimeInSec;
		private AbstractBinaryDecoder decoder;
		private AbstractBinaryEncoder encoder;
		private HashSet<String> whiteList;

		public BinaryServerConfigBuilder() {
			this.serverName = "Binary-Server";
			this.port = 8888;
			this.connectionPass = "limitart-core";
			this.connectionValidateTimeInSec = 20;
			this.decoder = AbstractBinaryDecoder.DEFAULT_DECODER;
			this.encoder = AbstractBinaryEncoder.DEFAULT_ENCODER;
			this.whiteList = new HashSet<>();
		}

		/**
		 * 构建配置
		 * 
		 * @return
		 */
		public BinaryServerConfig build() {
			return new BinaryServerConfig(this);
		}

		/**
		 * 自定义解码器
		 * 
		 * @param decoder
		 * @return
		 */
		public BinaryServerConfigBuilder decoder(AbstractBinaryDecoder decoder) {
			this.decoder = decoder;
			return this;
		}

		public BinaryServerConfigBuilder encoder(AbstractBinaryEncoder encoder) {
			this.encoder = encoder;
			return this;
		}

		public BinaryServerConfigBuilder serverName(String serverName) {
			this.serverName = serverName;
			return this;
		}

		/**
		 * 绑定端口
		 * 
		 * @param port
		 * @return
		 */
		public BinaryServerConfigBuilder port(int port) {
			if (port >= 1024) {
				this.port = port;
			}
			return this;
		}

		/**
		 * 链接验证密码
		 * 
		 * @param connectionPass
		 * @return
		 */
		public BinaryServerConfigBuilder connectionPass(String connectionPass) {
			this.connectionPass = connectionPass;
			return this;
		}

		/**
		 * 链接验证超时(秒)
		 * 
		 * @param connectionValidateTimeInSec
		 * @return
		 */
		public BinaryServerConfigBuilder connectionValidateTimeInSec(int connectionValidateTimeInSec) {
			this.connectionValidateTimeInSec = connectionValidateTimeInSec;
			return this;
		}
		
		public BinaryServerConfigBuilder whiteList(String ... remoteAddress){
			for(String ip : remoteAddress){
				if(StringUtil.isIp(ip)){
					this.whiteList.add(ip);
				}
			}
			return this;
		}
	}
}