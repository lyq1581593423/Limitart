/*
 * Copyright (c) 2016-present The Limitart Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.slingerxv.limitart.rpcx.providerx;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slingerxv.limitart.net.binary.BinaryClient;
import org.slingerxv.limitart.net.binary.BinaryServer;
import org.slingerxv.limitart.net.binary.handler.IHandler;
import org.slingerxv.limitart.net.binary.message.MessageFactory;
import org.slingerxv.limitart.net.struct.AddressPair;
import org.slingerxv.limitart.rpcx.define.ServiceX;
import org.slingerxv.limitart.rpcx.exception.ServiceError;
import org.slingerxv.limitart.rpcx.exception.ServiceXProxyException;
import org.slingerxv.limitart.rpcx.message.schedule.AddScheduleToServiceCenterProviderMessage;
import org.slingerxv.limitart.rpcx.message.schedule.TriggerScheduleServiceCenterToProviderServiceCenterMessage;
import org.slingerxv.limitart.rpcx.message.service.DirectFetchProviderServicesMessage;
import org.slingerxv.limitart.rpcx.message.service.DirectFetchProviderServicesResultMessage;
import org.slingerxv.limitart.rpcx.message.service.PushServiceToServiceCenterProviderMessage;
import org.slingerxv.limitart.rpcx.message.service.RpcExecuteClientMessage;
import org.slingerxv.limitart.rpcx.message.service.RpcResultServerMessage;
import org.slingerxv.limitart.rpcx.providerx.config.ProviderXConfig;
import org.slingerxv.limitart.rpcx.providerx.listener.IProviderListener;
import org.slingerxv.limitart.rpcx.providerx.schedule.ProviderJob;
import org.slingerxv.limitart.rpcx.providerx.struct.RpcServiceInstance;
import org.slingerxv.limitart.rpcx.struct.RpcProviderName;
import org.slingerxv.limitart.rpcx.util.RpcUtil;
import org.slingerxv.limitart.util.ReflectionUtil;
import org.slingerxv.limitart.util.StringUtil;

import io.netty.channel.Channel;

/**
 * RPC服务提供者
 *
 * @author hank
 */
public class ProviderX {
	private static Logger log = LoggerFactory.getLogger(ProviderX.class);
	private BinaryServer server;
	private BinaryClient serviceCenterClient;
	private IProviderListener providerListener;
	private ProviderXConfig config;
	private HashMap<String, RpcServiceInstance> services = new HashMap<>();
	// 定时任务回调列表
	private HashMap<String, ProviderJob> scheduleJobs = new HashMap<>();

	public ProviderX(ProviderXConfig config) throws Exception {
		this(config, null);
	}

	public ProviderX(ProviderXConfig config, IProviderListener providerListener) throws Exception {
		this.providerListener = providerListener;
		this.config = Objects.requireNonNull(config, "config");
		MessageFactory factory = new MessageFactory();
		// 初始化内部消息
		factory.registerMsg(new RpcExecuteClientHandler());
		factory.registerMsg(new DirectFetchProverServicesHandler());
		server = new BinaryServer.BinaryServerBuilder().addressPair(new AddressPair(config.getMyPort()))
				.serverName("RPC-Provider").factory(factory).dispatchMessage((message, handler) -> {
					message.setExtra(this);
					try {
						handler.handle(message);
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}).onServerBind(channel -> {
					if (this.providerListener != null) {
						this.providerListener.onProviderBind(this);
					}
				}).build();
		// 处理服务中心模式
		if (this.config.getServiceCenterIp() != null) {
			MessageFactory centerFacotry = new MessageFactory();
			centerFacotry.registerMsg(new TriggerScheduleServiceCenterToProviderServiceCenterHandler());
			serviceCenterClient = new BinaryClient.BinaryClientBuilder().autoReconnect(5)
					.remoteAddress(
							new AddressPair(this.config.getServiceCenterIp(), this.config.getServiceCenterPort()))
					.factory(centerFacotry).onConnectionEffective(client -> {
						// 链接生效，发布服务
						pushServicesToCenter();
						if (this.providerListener != null) {
							this.providerListener.onServiceCenterConnected(ProviderX.this);
						}
					}).dispatchMessage((message, handler) -> {
						message.setExtra(this);
						try {
							handler.handle(message);
						} catch (Exception e) {
							log.error(e.getMessage(), e);
						}
					}).build();
		}
	}

	public void bind() throws Exception {
		initAllServices();
		if (serviceCenterClient != null) {
			serviceCenterClient.connect();
		}
		server.startServer();
	}

	public void stop() {
		if (serviceCenterClient != null) {
			serviceCenterClient.disConnect();
		}
		if (server != null) {
			server.stopServer();
		}
	}

	/**
	 * 扫描本地服务
	 *
	 * @param packageName
	 * @throws ServiceXProxyException
	 * @throws IOException
	 * @throws ReflectiveOperationException
	 * @throws Exception
	 */
	private void initAllServices() throws ServiceXProxyException, IOException, ReflectiveOperationException {
		services.clear();
		List<Class<?>> classesByPackage = new ArrayList<>();
		for (String temp : this.config.getServicePackages()) {
			log.info("开始在包：" + temp + "下查找接口...");
			classesByPackage.addAll(ReflectionUtil.getClassesByPackage(temp, Object.class));
		}
		// RPC接口集合
		HashMap<Class<?>, HashMap<String, Method>> rpcInterfaces = new HashMap<>();
		// 查找所有RPC接口
		for (Class<?> clazz : classesByPackage) {
			// 必须是一个接口
			ServiceX annotation = clazz.getAnnotation(ServiceX.class);
			if (annotation == null) {
				continue;
			}
			if (!clazz.isInterface()) {
				throw new ServiceXProxyException(clazz.getName() + "RPC服务器必须是一个接口！");
			}
			// 检查参数是否符合标准
			String provider = annotation.provider();
			if (StringUtil.isEmptyOrNull(provider)) {
				throw new ServiceXProxyException("RPC接口提供商不能为空！");
			}
			String serviceName = RpcUtil.getServiceName(new RpcProviderName(provider), clazz);
			if (services.containsKey(serviceName)) {
				throw new ServiceXProxyException("服务名：" + serviceName + "重复！");
			}
			// 检查方法
			Method[] methods = clazz.getMethods();
			HashMap<String, Method> methodSet = new HashMap<>();
			// 检查方法参数是否合法
			for (Method method : methods) {
				String methodOverloadName = ReflectionUtil.getMethodOverloadName(method);
				// 检查参数
				Class<?>[] parameterTypes = method.getParameterTypes();
				for (Class<?> paramsType : parameterTypes) {
					RpcUtil.checkParamType(paramsType);

				}
				// 检查返回参数是否合法
				RpcUtil.checkParamType(method.getReturnType());
				// 异常抛出检查
				Class<?>[] exceptionTypes = method.getExceptionTypes();
				if (exceptionTypes == null || exceptionTypes.length < 1) {
					throw new ServiceXProxyException("类" + clazz.getName() + "的方法" + methodOverloadName + "必须要抛出异常："
							+ Exception.class.getName());
				}
				boolean exOk = false;
				for (Class<?> ex : exceptionTypes) {
					if (ex == Exception.class) {
						exOk = true;
					}
				}
				if (!exOk) {
					throw new ServiceXProxyException("类" + clazz.getName() + "的方法" + methodOverloadName + "的异常抛出必须有："
							+ Exception.class.getName());
				}
				methodSet.put(ReflectionUtil.getMethodOverloadName(method), method);
			}
			rpcInterfaces.put(clazz, methodSet);
		}
		// 查找RPC接口的实现类
		List<Class<?>> classesByPackage2 = ReflectionUtil.getClassesByPackage(this.config.getServiceImplPackages(),
				Object.class);
		log.info("开始在包：" + this.config.getServiceImplPackages() + "下查找接口实现...");
		for (Class<?> clazz : classesByPackage2) {
			Class<?>[] interfaces = clazz.getInterfaces();
			if (interfaces == null || interfaces.length < 1) {
				continue;
			}
			// 检查实现的接口实例的所有RPC服务
			HashMap<String, Class<?>> serviceNames = new HashMap<>();
			Object instance = null;
			// 遍历接口（主要处理一个实例，实现了多个RPC接口的情况）
			for (Class<?> temp : interfaces) {
				HashMap<String, Method> hashMap = rpcInterfaces.get(temp);
				// 没有RPC服务
				if (hashMap == null) {
					continue;
				}
				ServiceX annotation = temp.getAnnotation(ServiceX.class);
				// 此类有实现此RPC接口
				serviceNames.put(RpcUtil.getServiceName(new RpcProviderName(annotation.provider()), temp), temp);
				if (instance == null) {
					instance = clazz.newInstance();
				}
			}
			// 如果查找到了实例
			if (instance != null && !serviceNames.isEmpty()) {
				for (Entry<String, Class<?>> entry : serviceNames.entrySet()) {
					String serviceName = entry.getKey();
					if (services.containsKey(serviceName)) {
						throw new ServiceXProxyException("服务：" + serviceName + "发现了多个实现类：" + instance);
					}
					RpcServiceInstance data = new RpcServiceInstance();
					data.setInstance(instance);
					Class<?> value = entry.getValue();
					data.getMethods().putAll(rpcInterfaces.get(value));
					services.put(serviceName, data);
					log.info("发现服务：" + serviceName + "，实例名称："
							+ (clazz.getName() + "@" + Integer.toHexString(instance.hashCode())));
				}
			}
		}
	}

	/**
	 * 执行RPC消费者请求的方法
	 *
	 * @param context
	 * @param requestId
	 * @param moduleName
	 * @param methodName
	 * @param params
	 */
	private void executeRPC(Channel channel, int requestId, String moduleName, String methodName, List<Object> params)
			throws Exception {
		RpcResultServerMessage msg = new RpcResultServerMessage();
		msg.setRequestId(requestId);
		msg.setErrorCode(0);
		try {
			RpcServiceInstance serviceInstanceData = services.get(moduleName);
			if (serviceInstanceData == null) {
				log.error("RPC消费者：" + channel.remoteAddress() + "发送了未知的服务名：" + moduleName);
				msg.setErrorCode(ServiceError.SERVER_HAS_NO_MODULE);
				return;
			}
			Method method = serviceInstanceData.getMethods().get(methodName);
			if (method == null) {
				log.error("RPC消费者：" + channel.remoteAddress() + "发送了未知的方法名：" + methodName + "，服务名为：" + moduleName);
				msg.setErrorCode(ServiceError.SERVER_HAS_NO_METHOD);
				return;
			}
			if (msg.getErrorCode() == 0) {
				try {
					Object result = method.invoke(serviceInstanceData.self(), params.toArray());
					if (result != null) {
						msg.setReturnType(result.getClass().getName());
						msg.setReturnVal(result);
					}
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		} finally {
			try {
				server.sendMessage(channel, msg, null);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	 * 直接返回服务列表给客户端
	 *
	 * @param context
	 */
	private void directPushServices(Channel channel) {
		DirectFetchProviderServicesResultMessage msg = new DirectFetchProviderServicesResultMessage();
		msg.providerId = this.config.getProviderUID();
		msg.services.addAll(services.keySet());
		try {
			server.sendMessage(channel, msg, null);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	/**
	 * 发布服务到服务中心
	 */
	private void pushServicesToCenter() {
		log.info("开始发布自己的服务到服务中心...");
		PushServiceToServiceCenterProviderMessage msg = new PushServiceToServiceCenterProviderMessage();
		msg.myIp = this.config.getMyIp();
		msg.myPort = this.config.getMyPort();
		msg.providerUID = this.config.getProviderUID();
		for (String serviceName : services.keySet()) {
			msg.services.add(serviceName);
			log.info("发布服务到服务中心：" + serviceName);
		}
		try {
			serviceCenterClient.sendMessage(msg, null);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	/**
	 * 获取服务实体
	 *
	 * @param provider
	 * @param clazz
	 * @return
	 * @throws ServiceXProxyException
	 */
	@SuppressWarnings("unchecked")
	public <T> T getServiceInstance(RpcProviderName provider, Class<T> clazz) throws ServiceXProxyException {
		ServiceX annotation = clazz.getAnnotation(ServiceX.class);
		if (annotation == null) {
			throw new ServiceXProxyException(clazz.getName() + "is not ServiceX!");
		}
		RpcServiceInstance serviceInstanceData = services.get(RpcUtil.getServiceName(provider, clazz));
		if (serviceInstanceData == null) {
			return null;
		}
		return (T) serviceInstanceData.self();
	}

	/**
	 * 发布定时任务
	 *
	 * @param job
	 * @throws Exception
	 */
	public void schedule(ProviderJob job) throws Exception {
		if (this.serviceCenterClient == null) {
			throw new Exception("此Provider不是服务中心模式");
		}
		String jobName = job.getJobName();
		if (scheduleJobs.containsKey(jobName)) {
			throw new Exception("重复的JobName:" + jobName);
		}
		AddScheduleToServiceCenterProviderMessage msg = new AddScheduleToServiceCenterProviderMessage();
		msg.jobName = job.getJobName();
		msg.providerId = this.config.getProviderUID();
		msg.cronExpression = job.getCronExpression();
		msg.intervalInHours = job.getIntervalInHours();
		msg.intervalInMinutes = job.getIntervalInMinutes();
		msg.intervalInSeconds = job.getIntervalInSeconds();
		msg.intervalInMillis = job.getIntervalInMillis();
		msg.repeatCount = job.getRepeatCount();
		serviceCenterClient.sendMessage(msg, (isSuccess, cause, channel) -> {
			if (isSuccess) {
				scheduleJobs.put(jobName, job);
				log.info("注册一个定时任务到服务中心：" + job.toString());
			} else {
				log.error("注册定时任务到服务中心失败：" + job.toString());
			}
		});
	}

	private void onScheduleTrigger(String jobName, boolean end) {
		ProviderJob providerJob = scheduleJobs.get(jobName);
		if (providerJob == null) {
			log.error("定时任务触发：" + jobName + "，找不到回调！");
			return;
		}
		if (end) {
			scheduleJobs.remove(jobName);
			log.info("服务中心通知任务生命终结，执行删除：" + jobName);
		}
		providerJob.getListener().action();
	}

	public class RpcExecuteClientHandler implements IHandler<RpcExecuteClientMessage> {

		@Override
		public void handle(RpcExecuteClientMessage msg) {
			try {
				((ProviderX) msg.getExtra()).executeRPC(msg.getChannel(), msg.requestId, msg.moduleName, msg.methodName,
						msg.params);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	public class DirectFetchProverServicesHandler implements IHandler<DirectFetchProviderServicesMessage> {

		@Override
		public void handle(DirectFetchProviderServicesMessage msg) {
			((ProviderX) msg.getExtra()).directPushServices(msg.getChannel());
		}

	}

	public class TriggerScheduleServiceCenterToProviderServiceCenterHandler
			implements IHandler<TriggerScheduleServiceCenterToProviderServiceCenterMessage> {

		@Override
		public void handle(TriggerScheduleServiceCenterToProviderServiceCenterMessage msg) {
			String jobName = msg.getJobName();
			((ProviderX) msg.getExtra()).onScheduleTrigger(jobName, msg.isEnd());
		}
	}
}
