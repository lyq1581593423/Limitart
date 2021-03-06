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
package org.slingerxv.limitart.script.define;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slingerxv.limitart.script.constant.ScriptFileType;
import org.slingerxv.limitart.script.exception.ScriptException;

import groovy.lang.GroovyClassLoader;

/**
 * 服务器脚本管理
 * 
 * @author hank
 *
 * @param <KEY>
 *            脚本Id
 */
public abstract class AbstractScriptLoader<KEY> {
	protected static final Logger log = LoggerFactory.getLogger(AbstractScriptLoader.class);
	protected ConcurrentHashMap<KEY, IScript<KEY>> scriptMap = new ConcurrentHashMap<>();
	protected ConcurrentHashMap<KEY, File> scriptPath = new ConcurrentHashMap<>();
	private AtomicLong dynamicCodeCount = new AtomicLong(100000);

	public void foreach(BiConsumer<? super KEY, ? super IScript<KEY>> action) {
		scriptMap.forEach(action);
	}

	/**
	 * 获取脚本
	 * 
	 * @param scriptId
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T extends IScript<KEY>> T getScript(KEY scriptId) {
		return (T) scriptMap.get(scriptId);
	}

	/**
	 * 执行一段继承了IDynamicCode的代码
	 * 
	 * @param path
	 * @return
	 * @throws ScriptException
	 * @throws IOException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @see IDynamicCode
	 */
	public AbstractScriptLoader<KEY> executeCommand(String path)
			throws ScriptException, IOException, InstantiationException, IllegalAccessException {
		File file = new File(path);
		if (!file.exists()) {
			throw new FileNotFoundException("file not exist!");
		}
		if (file.isDirectory()) {
			throw new ScriptException("script file is directory!");
		}
		String[] split = file.getName().split("[.]");
		if (split.length < 2) {
			throw new ScriptException("file name must has extension,like .java .groovy,yours:" + file.getName());
		}
		String type = split[1];
		ScriptFileType typeByValue = ScriptFileType.getTypeByValue(type);
		if (typeByValue == null) {
			throw new ScriptException("script type not supported:" + type);
		}
		try (GroovyClassLoader loader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader())) {
			@SuppressWarnings("rawtypes")
			Class parseClass = loader.parseClass(file);
			Object newInstance = parseClass.newInstance();
			if (!(newInstance instanceof IDynamicCode)) {
				throw new ScriptException("class must extends IDynamicCode");
			}
			IDynamicCode temp = (IDynamicCode) newInstance;
			log.info("compile code success,start executing...");
			temp.execute();
			log.info("done!");
		}
		return this;
	}

	/**
	 * 执行几串简单的命令
	 * 
	 * @param importList
	 * @param commandLines
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IOException
	 * @throws ScriptException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	public AbstractScriptLoader<KEY> executeCommand(List<String> importList, String commandLines)
			throws InstantiationException, IllegalAccessException, IOException, ScriptException,
			IllegalArgumentException, InvocationTargetException {
		StringBuilder importBuffer = new StringBuilder();
		if (importList != null) {
			for (String temp : importList) {
				importBuffer.append("import " + temp + ";");
			}
		}
		String result = importBuffer.toString()
				+ "import com.limitart.script.IDynamicCode; public class DynamicCodeProxy"
				+ dynamicCodeCount.getAndIncrement() + " extends IDynamicCode {" + " public void execute() {"
				+ commandLines + "}" + "}";
		try (GroovyClassLoader loader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader())) {
			@SuppressWarnings("rawtypes")
			Class parseClass = loader.parseClass(result);
			IDynamicCode newInstance = (IDynamicCode) parseClass.newInstance();
			log.info("compile code success,start executing...");
			newInstance.execute();
			log.info("done!");
		}
		return this;
	}
}
