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
package org.slingerxv.limitart.game.bag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.slingerxv.limitart.funcs.Test1;
import org.slingerxv.limitart.game.bag.exception.BagFullException;
import org.slingerxv.limitart.game.bag.exception.BagGridOcuppiedException;
import org.slingerxv.limitart.game.bag.exception.ItemNotExistException;
import org.slingerxv.limitart.game.bag.exception.ItemNotSameTypeException;
import org.slingerxv.limitart.game.bag.exception.ItemOverStackException;
import org.slingerxv.limitart.game.bag.exception.ItemSliptNotEnoughNumException;
import org.slingerxv.limitart.game.bag.exception.ItemZeroNumException;
import org.slingerxv.limitart.game.item.AbstractItem;
import org.slingerxv.limitart.util.Beta;

/**
 * 包裹
 * 
 * @author hank
 *
 */
@Beta
public abstract class Bag {
	private static final int START_GRID = 0;

	/**
	 * 是否能添加物品
	 * 
	 * @param item
	 * @return
	 * @throws ItemZeroNumException
	 * @throws BagFullException
	 * @throws ItemNotExistException
	 */
	public void canAddItem(AbstractItem item) throws ItemZeroNumException, BagFullException, ItemNotExistException {
		if (item == null) {
			throw new ItemNotExistException();
		}
		if (item.getNum() <= 0) {
			throw new ItemZeroNumException();
		}
		if (remain() <= 0) {
			throw new BagFullException();
		}
	}

	/**
	 * 能否批量添加物品
	 * 
	 * @param items
	 * @return
	 * @throws ItemZeroNumException
	 * @throws BagFullException
	 * @throws ItemNotExistException
	 */
	public void canAddItems(List<AbstractItem> items)
			throws ItemZeroNumException, BagFullException, ItemNotExistException {
		if (items.isEmpty()) {
			throw new ItemNotExistException();
		}
		for (AbstractItem item : items) {
			if (item.getNum() <= 0) {
				throw new ItemZeroNumException();
			}
		}
		if (remain() < items.size()) {
			throw new BagFullException();
		}
	}

	/**
	 * 添加一个物品
	 * 
	 * @param gridId
	 * @param item
	 * @return
	 * @throws BagFullException
	 * @throws BagGridOcuppiedException
	 * @throws ItemZeroNumException
	 * @throws ItemNotExistException
	 */
	public void addItem(int gridId, AbstractItem item)
			throws BagFullException, BagGridOcuppiedException, ItemZeroNumException, ItemNotExistException {
		if (gridId < START_GRID || gridId > capacity() - 1 || bag().containsKey(gridId)) {
			gridId = getAnEmptyGrid();
		}
		if (gridId == -1) {
			throw new BagFullException();
		}
		canAddItem(item);
		bag().put(gridId, item);
		onItemAdded(gridId, item);
	}

	/**
	 * 添加一个物品
	 * 
	 * @param item
	 * @return
	 * @throws BagFullException
	 * @throws BagGridOcuppiedException
	 * @throws ItemZeroNumException
	 * @throws ItemNotExistException
	 */
	public void addItem(AbstractItem item)
			throws BagFullException, BagGridOcuppiedException, ItemZeroNumException, ItemNotExistException {
		addItem(Integer.MIN_VALUE, item);
	}

	/**
	 * 添加一串物品
	 * 
	 * @param items
	 * @return
	 * @throws BagFullException
	 * @throws BagGridOcuppiedException
	 * @throws ItemNotExistException
	 * @throws ItemZeroNumException
	 */
	public void addItems(List<AbstractItem> items)
			throws BagFullException, BagGridOcuppiedException, ItemNotExistException, ItemZeroNumException {
		if (items.isEmpty()) {
			throw new ItemNotExistException();
		}
		canAddItems(items);
		for (AbstractItem item : items) {
			addItem(item);
		}
	}

	/**
	 * 删除某物品
	 * 
	 * @param gridId
	 * @return
	 */
	public AbstractItem removeItem(int gridId) {
		AbstractItem remove = bag().remove(gridId);
		if (remove != null) {
			onItemRemoved(gridId, remove);
		}
		return remove;
	}

	/**
	 * 根据条件删除相关物品
	 * 
	 * @param filter
	 * @return
	 */
	public List<AbstractItem> removeItems(Test1<AbstractItem> filter) {
		List<AbstractItem> result = new ArrayList<>();
		Iterator<Entry<Integer, AbstractItem>> iterator = bag().entrySet().iterator();
		for (; iterator.hasNext();) {
			Entry<Integer, AbstractItem> next = iterator.next();
			if (filter.test(next.getValue())) {
				iterator.remove();
				result.add(next.getValue());
				onItemRemoved(next.getKey(), next.getValue());
			}
		}
		return result;

	}

	/**
	 * 获取相应格子的物品
	 * 
	 * @param gridId
	 * @return
	 */
	public AbstractItem getItem(int gridId) {
		return bag().get(gridId);
	}

	/**
	 * 获取符合条件的物品
	 * 
	 * @param filter
	 * @return
	 */
	public List<AbstractItem> getItems(Test1<AbstractItem> filter) {
		List<AbstractItem> result = new ArrayList<>();
		for (AbstractItem item : bag().values()) {
			if (filter.test(item)) {
				result.add(item);
			}
		}
		return result;

	}

	/**
	 * 获取符合条件的物品的数量
	 * 
	 * @param filter
	 * @return
	 */
	public int getItemCount(Test1<AbstractItem> filter) {
		int count = 0;
		for (AbstractItem item : bag().values()) {
			if (filter.test(item)) {
				count += item.getNum();
			}
		}
		return count;

	}

	/**
	 * 整理
	 * 
	 * @return
	 */
	public synchronized void makeUp() {
		ConcurrentHashMap<Integer, AbstractItem> tempGrids = new ConcurrentHashMap<>();
		// 全部尝试合并
		for (int i = START_GRID; i < capacity(); ++i) {
			for (int j = i + 1; j < capacity(); ++j) {
				try {
					merge(i, j);
				} catch (ItemNotExistException | ItemNotSameTypeException | ItemOverStackException
						| ItemZeroNumException e) {
				}
			}
		}
		// 开始排序
		List<AbstractItem> tempList = new ArrayList<>(bag().values());
		Collections.sort(tempList);
		for (int i = START_GRID; i < capacity(); ++i) {
			AbstractItem newItem = null;
			if (i < tempList.size()) {
				newItem = tempList.get(i);
				tempGrids.put(i, newItem);
			}
			AbstractItem oldItem = bag().get(i);
			if (newItem != oldItem) {
				if (oldItem != null) {
					onItemRemoved(i, oldItem);
				}
				if (newItem != null) {
					onItemAdded(i, newItem);
				}
			}
		}
		bag().clear();
		bag().putAll(tempGrids);
	}

	/**
	 * 拆分物品
	 * 
	 * @param item
	 * @param num
	 * @throws ItemNotExistException
	 * @throws ItemSliptNotEnoughNumException
	 * @throws BagFullException
	 * @throws BagGridOcuppiedException
	 * @throws ItemZeroNumException
	 */
	private void split(AbstractItem item, int num) throws ItemNotExistException, ItemSliptNotEnoughNumException,
			BagFullException, BagGridOcuppiedException, ItemZeroNumException {
		if (item == null) {
			throw new ItemNotExistException();
		}
		if (item.getNum() <= 1 || num > item.getNum()) {
			throw new ItemSliptNotEnoughNumException(item.getNum(), num);
		}
		int anEmptyGrid = getAnEmptyGrid();
		if (anEmptyGrid == -1) {
			throw new BagFullException();
		}
		int reduceNum = Math.max(1, num);
		item.setNum(item.getNum() - reduceNum);
		onItemChanged(item);
		AbstractItem copy = item.copy();
		copy.setNum(reduceNum);
		addItem(anEmptyGrid, copy);
	}

	/**
	 * 拆分物品
	 * 
	 * @param gridId
	 * @param num
	 * @return
	 * @throws BagFullException
	 * @throws ItemSliptNotEnoughNumException
	 * @throws ItemNotExistException
	 * @throws BagGridOcuppiedException
	 * @throws ItemZeroNumException
	 */
	public void split(int gridId, int num) throws ItemNotExistException, ItemSliptNotEnoughNumException,
			BagFullException, BagGridOcuppiedException, ItemZeroNumException {
		AbstractItem item = getItem(gridId);
		split(item, num);
		if (item.getNum() <= 0) {
			removeItem(gridId);
		}
	}

	/**
	 * 合并物品
	 * 
	 * @param me
	 * @param another
	 * @throws ItemNotSameTypeException
	 * @throws ItemOverStackException
	 * @throws ItemNotExistException
	 * @throws ItemZeroNumException
	 */
	private void merge(AbstractItem me, AbstractItem another)
			throws ItemNotSameTypeException, ItemOverStackException, ItemNotExistException, ItemZeroNumException {
		if (me == null || another == null) {
			throw new ItemNotExistException();
		}
		if (me.getNum() <= 0 || another.getNum() <= 0) {
			throw new ItemZeroNumException();
		}
		if (!me.isSameType(another)) {
			throw new ItemNotSameTypeException();
		}
		int meNum = me.getNum();
		int anotherNum = another.getNum();
		if (meNum >= me.getMaxStackNumber()) {
			throw new ItemOverStackException(me.getMaxStackNumber());
		}
		int meAfterNum = Math.min(me.getMaxStackNumber(), meNum + anotherNum);
		me.setNum(meAfterNum);
		onItemChanged(me);
		another.setNum(another.getNum() - (meAfterNum - meNum));
		onItemChanged(another);
	}

	/**
	 * 合并物品
	 * 
	 * @param gridOne
	 * @param gridAnother
	 * @throws ItemNotExistException
	 * @throws ItemZeroNumException
	 * @throws ItemOverStackException
	 * @throws ItemNotSameTypeException
	 */
	public void merge(int gridOne, int gridAnother)
			throws ItemNotExistException, ItemNotSameTypeException, ItemOverStackException, ItemZeroNumException {
		AbstractItem me = getItem(gridOne);
		AbstractItem another = getItem(gridAnother);
		merge(me, another);
		if (another.getNum() <= 0) {
			removeItem(gridAnother);
		}
	}

	/**
	 * 获取一个空的格子
	 * 
	 * @return -1为没有空格子
	 */
	public int getAnEmptyGrid() {
		for (int i = START_GRID; i < capacity(); ++i) {
			if (bag().get(i) == null) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 内容大小
	 * 
	 * @return
	 */
	public int size() {
		return bag().size();
	}

	/**
	 * 剩余空间
	 * 
	 * @return
	 */
	public int remain() {
		return Math.max(0, capacity() - size());
	}

	protected abstract Map<Integer, AbstractItem> bag();

	/**
	 * 容量
	 * 
	 * @return
	 */
	public abstract int capacity();

	/**
	 * 添加物品后
	 * 
	 * @param gridId
	 * @param item
	 */
	protected abstract void onItemAdded(int gridId, AbstractItem item);

	/**
	 * 删除物品后
	 * 
	 * @param gridId
	 * @param item
	 */
	protected abstract void onItemRemoved(int gridId, AbstractItem item);

	/**
	 * 物品信息产生变化
	 * 
	 * @param item
	 */
	public abstract void onItemChanged(AbstractItem item);
}
