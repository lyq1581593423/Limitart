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
package org.slingerxv.limitart.collections.define;

import java.util.List;
import java.util.Map;

import org.slingerxv.limitart.funcs.Func;

public interface IRankMap<K, V extends Func<K>> extends Map<K, V> {

	/**
	 * 集合大小
	 * 
	 * @return
	 */
	int size();

	/**
	 * 找到此Key在排行榜的名次
	 * 
	 * @param key
	 * @return
	 */
	int getIndex(K key);

	/**
	 * 获取一个范围的数据
	 * 
	 * @param start
	 * @param end
	 * @return
	 */
	List<V> getRange(int start, int end);

	List<V> getAll();

	/**
	 * 获取指定位置的元数
	 * 
	 * @param index
	 * @return
	 */
	V getAt(int index);

	void clear();

}