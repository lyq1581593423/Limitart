package com.limitart.net.http.util;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map.Entry;

import com.limitart.collections.ConstraintMap;
import com.limitart.net.http.constant.ContentTypes;
import com.limitart.net.http.constant.RequestErrorCode;
import com.limitart.util.StringUtil;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

public final class HttpUtil {
	private HttpUtil() {
	}

	public static byte[] post(String hostUrl, ConstraintMap<String> param, HashMap<String, String> requestProperty)
			throws IOException {
		HttpURLConnection conn = null;
		URL url = new URL(hostUrl);
		conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(5000);
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setDoInput(true);
		if (requestProperty != null) {
			for (Entry<String, String> entry : requestProperty.entrySet()) {
				conn.setRequestProperty(entry.getKey(), entry.getValue());
			}
		}
		try (DataOutputStream ds = new DataOutputStream(conn.getOutputStream());) {
			ds.write(HttpUtil.map2QueryParam(param).getBytes("utf-8"));
		}
		// 判断是否正常响应数据
		if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
			return null;
		}
		try (DataInputStream inputStream = new DataInputStream(conn.getInputStream());
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();) {
			byte[] b = new byte[1024];
			int l;
			while ((l = inputStream.read(b)) > 0) {
				buffer.write(b, 0, l);
			}
			return buffer.toByteArray();
		} finally {
			if (conn != null) {
				conn.disconnect(); // 中断连接
			}
		}

	}

	public static byte[] get(String hostUrl) throws IOException {
		return get(hostUrl, null);
	}

	public static byte[] get(String hostUrl, ConstraintMap<String> param) throws IOException {
		HttpURLConnection conn = null;
		URL url;
		if (param == null) {
			url = new URL(hostUrl);
		} else {
			url = new URL(hostUrl + "?" + map2QueryParam(param));
		}
		conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(5000);
		conn.setRequestMethod("GET");
		conn.connect();
		// 判断是否正常响应数据
		if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
			return null;
		}
		try (DataInputStream inputStream = new DataInputStream(conn.getInputStream());
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();) {
			byte[] b = new byte[1024];
			int l;
			while ((l = inputStream.read(b)) > 0) {
				buffer.write(b, 0, l);
			}
			return buffer.toByteArray();
		} finally {
			if (conn != null) {
				conn.disconnect(); // 中断连接
			}
		}
	}

	public static void sendResponse(Channel channel, HttpResponseStatus resultCode, String result, boolean isClose) {
		ByteBuf buf = Unpooled.copiedBuffer(result.getBytes(CharsetUtil.UTF_8));
		sendResponse(channel, resultCode, ContentTypes.text_plain, buf, isClose);
	}

	public static void sendResponse(Channel channel, HttpResponseStatus resultCode, ContentTypes contentType,
			ByteBuf result, boolean isClose) {
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, resultCode, result);
		response.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType.getValue());
		response.headers().add(HttpHeaderNames.CONTENT_LENGTH, result.readableBytes() + "");
		response.headers().add(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
		response.headers().add(HttpHeaderNames.CONTENT_DISPOSITION, "inline;filename=\"stupid.jpg\"");
		ChannelFuture future = channel.writeAndFlush(response);
		if (isClose) {
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}

	public static void sendResponse(Channel channel, FullHttpRequest fullHttpRequest, HttpResponseStatus resultCode,
			String result) {
		boolean close = fullHttpRequest.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
				|| fullHttpRequest.protocolVersion().equals(HttpVersion.HTTP_1_0) && !fullHttpRequest.headers()
						.contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true);
		sendResponse(channel, resultCode, result, close);
	}

	public static void sendResponseError(Channel channel, FullHttpRequest fullHttpRequest, RequestErrorCode errorCode,
			String others) {
		sendResponse(channel, HttpResponseStatus.BAD_GATEWAY, "ErrorCode:" + errorCode.getValue() + " Info:" + others,
				true);
	}

	public static void sendResponseError(Channel channel, FullHttpRequest fullHttpRequest, RequestErrorCode errorCode) {
		sendResponseError(channel, fullHttpRequest, errorCode, errorCode.toString());
	}

	public static String map2QueryParam(ConstraintMap<String> map) {
		StringBuilder queryParamBuffer = new StringBuilder();
		for (Entry<String, Object> entry : map.entrySet()) {
			queryParamBuffer.append(entry.getKey()).append("=").append(entry.getValue().toString()).append("&");
		}
		if (queryParamBuffer.length() > 0) {
			queryParamBuffer.deleteCharAt(queryParamBuffer.length() - 1);
		}
		return queryParamBuffer.toString();
	}

	public static ConstraintMap<String> queryParam2Map(String queryParam) throws Exception {
		ConstraintMap<String> map = new ConstraintMap<String>();
		if (!StringUtil.isEmptyOrNull(queryParam)) {
			String[] split2 = queryParam.split("[&]");
			for (String temp : split2) {
				String[] split3 = temp.split("[=]");
				if (split3.length == 2) {
					map.putObject(split3[0], split3[1]);
				}
			}
		}
		return map;
	}
}
