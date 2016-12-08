/*
 * Copyright 2012-2016 École polytechnique fédérale de Lausanne (EPFL), Switzerland
 * Copyright 2012-2016 Crossing-Tech SA, Switzerland
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.javabip.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

// TODO: Auto-generated Javadoc
/**
 * The Class HelperFunctions.
 * 
 * @author Anastasia Mavridou
 */
public class HelperFunctions {

	/**
	 * Adds the all.
	 *
	 * @param list
	 *            the list
	 * @param iterable
	 *            the iterable
	 */
	public static void addAll(ArrayList<Object> list, Iterable<Object> iterable) {
		for (Object port : iterable) {
			list.add(port);
		}
	}

	/**
	 * Adds the all.
	 *
	 * @param <E>
	 *            the element type
	 * @param list
	 *            the list
	 * @param iter
	 *            the iter
	 * @return the collection
	 */
	public static <E> Collection<E> addAll(Collection<E> list, Iterable<E> iter) {
		for (E item : iter) {
			list.add(item);
		}
		return list;
	}

	static private <T> ArrayList<HashSet<T>> _enumerateSubsets(ArrayList<T> baseSet, int size) {
		if (size == 1) {
			ArrayList<HashSet<T>> subsets = new ArrayList<HashSet<T>>();
			for (T element : baseSet) {
				HashSet<T> subset = new HashSet<T>();
				subset.add(element);
				subsets.add(subset);
			}
			return subsets;
		} else if (baseSet.size() == size) {
			ArrayList<HashSet<T>> subsets = new ArrayList<HashSet<T>>();
			subsets.add(new HashSet<T>(baseSet));
			return subsets;
		} else {
			T element = baseSet.remove(0);
			ArrayList<HashSet<T>> subsets = _enumerateSubsets(baseSet, size);
			for (HashSet<T> subset : _enumerateSubsets(baseSet, size - 1)) {
				subset.add(element);
				subsets.add(subset);
			}
			baseSet.add(0, element);
			return subsets;
		}
	}

	static public <T> ArrayList<HashSet<T>> enumerateSubsets(Collection<T> baseSet, int size) {
		return _enumerateSubsets(new ArrayList<T>(baseSet), size);
	}

	static public <T> ArrayList<HashSet<T>> enumerateAllSubsets(Collection<T> baseSet) {
		ArrayList<T> baseSetCopy = new ArrayList<T>(baseSet);
		ArrayList<HashSet<T>> subsets = new ArrayList<HashSet<T>>();
		subsets.add(new HashSet<T>()); // empty set

		for (int size = 1; size <= baseSet.size(); size++)
			subsets.addAll(_enumerateSubsets(baseSetCopy, size));

		return subsets;
	}

}
