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
package org.javabip.engine.coordinator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

/**
 * Contains helper functions to manipulate structures of data containers used by the Data Coordinator.
 * 
 * @author Alina Zolotukhina
 *
 */
public class DataHelper {

	/**
	 * Given a grouped list of data containers, the method creates a list of tuples consisting of name-value data pairs,
	 * necessary for port execution
	 * 
	 * @param containerList
	 *            a list of rows of containers with data, where each row provides all the data needed by a port
	 * @return a list of maps of name-value pairs, where each map provides all the data needed by a port
	 */
	public static ArrayList<Map<String, Object>> createDataValueMaps(
			ArrayList<ArrayList<DataContainer>> containerList) {

		ArrayList<Map<String, Object>> dataValues = new ArrayList<Map<String, Object>>();
		for (ArrayList<DataContainer> row : containerList) {
			Map<String, Object> dataValueRow = new Hashtable<String, Object>();
			for (DataContainer container : row) {
				dataValueRow.put(container.name(), container.value());
			}
			dataValues.add(dataValueRow);
		}
		return dataValues;
	}

	/**
	 * Given an unsorted list of data containers, creates a grouped list of data containers, where each sub-list is a
	 * permutation containing a value for each data request (one for each data name)
	 * 
	 * @param unsortedDataList
	 *            list of unsorted data containers
	 * @return a list of data containers grouped into sub-lists, each sub-list having a different tuple of data values,
	 *         and the size of sublist must be equal to the number of required data
	 */
	public static ArrayList<ArrayList<DataContainer>> getDataValueTable(
			ArrayList<DataContainer> unsortedDataList) {
		ArrayList<ArrayList<DataContainer>> result = new ArrayList<ArrayList<DataContainer>>();

		if (unsortedDataList == null || unsortedDataList.isEmpty()) {
			return result;
		}
		// the data is now grouped by the name
		ArrayList<ArrayList<DataContainer>> sortedList = dataGroupedByName(unsortedDataList);
		// for one bipData get iterator over its values
		ArrayList<DataContainer> entry = sortedList.get(0);
		Iterator<DataContainer> iterator = entry.iterator();

		// for each value of this first bipData
		while (iterator.hasNext()) {
			// create one map, where
			// all the different pairs name<->value will be stored
			// put there the current value of the first bipData
			ArrayList<DataContainer> dataRow = new ArrayList<DataContainer>();
			DataContainer next = iterator.next();
			dataRow.add(next);
			// remove the current data from the initial data table
			// so that it is not treated again further
			sortedList.remove(entry);
			// treat the other bipData variables
			result.addAll(getNextTableRow(sortedList, dataRow));
			// restore the current data
			sortedList.add(entry);
		}
		return result;
	}

	/**
	 * A recursive method to get all the permutations of a data values for the getDataValueTable method
	 * 
	 * @param sortedList
	 *            the list of data lists grouped by name
	 * @param dataRow
	 *            the data row currently being built
	 * @return the next table row of different data values
	 */
	private static ArrayList<ArrayList<DataContainer>> getNextTableRow(
			ArrayList<ArrayList<DataContainer>> sortedList, ArrayList<DataContainer> dataRow) {
		ArrayList<ArrayList<DataContainer>> result = new ArrayList<ArrayList<DataContainer>>();
		// if there is no more data left, it means we have constructed one map
		// of all the bipData variables
		if (sortedList == null || sortedList.isEmpty()) {
			result.add(dataRow);
			return result;
		}

		// for one bipData get iterator over its values
		ArrayList<DataContainer> entry = sortedList.iterator().next();
		Iterator<DataContainer> iterator = entry.iterator();

		// for each value of this bipData
		while (iterator.hasNext()) {
			// create a new map, where
			// all the different pairs name<->value will be stored
			// copy there all the previous values
			// (this must be done to escape
			// change of one variable that leads to change of all its copies
			ArrayList<DataContainer> thisRow = new ArrayList<DataContainer>();
			thisRow.addAll(dataRow);
			// put there the current value of the bipData
			thisRow.add(iterator.next());
			// remove the current data from the initial data table
			// so that it is not treated again further
			sortedList.remove(entry);
			// treat the other bipData variables
			result.addAll(getNextTableRow(sortedList, thisRow));
			// restore the current data
			sortedList.add(entry);
		}
		return result;
	}

	/**
	 * Transforms a list of data containers into a list of lists of data containers, where all data containers of each
	 * inner list are grouped by name.
	 * 
	 * @param unsortedList
	 *            the list of all data acquired from all components
	 * @return list of data lists grouped by name
	 */
	private static ArrayList<ArrayList<DataContainer>> dataGroupedByName(ArrayList<DataContainer> unsortedList) {
		ArrayList<ArrayList<DataContainer>> groupedByName = new ArrayList<ArrayList<DataContainer>>();
		// double loop, for each name find all similar, create a list out of
		// them and delete them all from the initial list
		while (!unsortedList.isEmpty()) {
			ArrayList<DataContainer> oneDataList = new ArrayList<DataContainer>();
			DataContainer data = unsortedList.get(0);
			oneDataList.add(data);
			unsortedList.remove(data);
			for (DataContainer d : unsortedList) {
				if (d.name().equals(data.name())) {
					oneDataList.add(d);
				}
			}
			unsortedList.removeAll(oneDataList);
			groupedByName.add(oneDataList);
		}
		return groupedByName;
	}

}
