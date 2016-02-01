package org.bip.engine.coordinator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

public class DataHelper {

	/**
	 * Given a grouped list of data containers,
	 * the method creates a list of tuples consisting of name-value data pairs, necessary for port execution
	 * 
	 * @param containerList
	 * 			a list of rows of containers with data, where each row provides all the data needed by a port
	 * @return a list of maps of name-value pairs, where each map provides all the data needed by a port
	 */
	public static ArrayList<Map<String, Object>> createDataValueMaps(
			ArrayList<ArrayList<DataContainerImpl>> containerList) {

		ArrayList<Map<String, Object>> dataValues = new ArrayList<Map<String, Object>>();
		for (ArrayList<DataContainerImpl> row : containerList) {
			Map<String, Object> dataValueRow = new Hashtable<String, Object>();
			for (DataContainerImpl container : row) {
				dataValueRow.put(container.name(), container.value());
			}
			dataValues.add(dataValueRow);
		}
		return dataValues;
	}

	/**
	 * Given an unsorted list of data containers, creates a grouped list of data
	 * containers, where each sub-list is a permutation containing a value for each data request 
	 * (one for each data name)
	 * 
	 * @param unsortedDataList
	 *            list of unsorted data containers
	 * @return a list of data containers grouped into sub-lists, 
	 * 			each sub-list having a different tuple of data values, 
	 * 			and the size of sublist must be equal to the number of required data 
	 */
	public static ArrayList<ArrayList<DataContainerImpl>> getDataValueTable(
			ArrayList<DataContainerImpl> unsortedDataList) {
		ArrayList<ArrayList<DataContainerImpl>> result = new ArrayList<ArrayList<DataContainerImpl>>();

		if (unsortedDataList == null || unsortedDataList.isEmpty()) {
			return result;
		}
		// the data is now grouped by the name
		ArrayList<ArrayList<DataContainerImpl>> sortedList = dataGroupedByName(
				unsortedDataList);
		// for one bipData get iterator over its values
		ArrayList<DataContainerImpl> entry = sortedList.get(0);
		Iterator<DataContainerImpl> iterator = entry.iterator();

		// for each value of this first bipData
		while (iterator.hasNext()) {
			// create one map, where
			// all the different pairs name<->value will be stored
			// put there the current value of the first bipData
			ArrayList<DataContainerImpl> dataRow = new ArrayList<DataContainerImpl>();
			DataContainerImpl next = iterator.next();
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
	private static ArrayList<ArrayList<DataContainerImpl>> getNextTableRow(
			ArrayList<ArrayList<DataContainerImpl>> sortedList,
			ArrayList<DataContainerImpl> dataRow) {
		ArrayList<ArrayList<DataContainerImpl>> result = new ArrayList<ArrayList<DataContainerImpl>>();
		// if there is no more data left, it means we have constructed one map
		// of all the bipData variables
		if (sortedList == null || sortedList.isEmpty()) {
			result.add(dataRow);
			return result;
		}

		// for one bipData get iterator over its values
		ArrayList<DataContainerImpl> entry = sortedList.iterator().next();
		Iterator<DataContainerImpl> iterator = entry.iterator();

		// for each value of this bipData
		while (iterator.hasNext()) {
			// create a new map, where
			// all the different pairs name<->value will be stored
			// copy there all the previous values
			// (this must be done to escape
			// change of one variable that leads to change of all its copies
			ArrayList<DataContainerImpl> thisRow = new ArrayList<DataContainerImpl>();
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
	 * Transforms a list of data containers into a list of lists of data
	 * containers, where all data containers of each inner list are grouped by
	 * name. 
	 * 
	 * @param unsortedList
	 *            the list of all data acquired from all components
	 * @return list of data lists grouped by name
	 */
	private static ArrayList<ArrayList<DataContainerImpl>> dataGroupedByName(
			ArrayList<DataContainerImpl> unsortedList) {
		ArrayList<ArrayList<DataContainerImpl>> groupedByName = new ArrayList<ArrayList<DataContainerImpl>>();
		// double loop, for each name find all similar, create a list out of
		// them and delete them all from the initial list
		while (!unsortedList.isEmpty()) {
			ArrayList<DataContainerImpl> oneDataList = new ArrayList<DataContainerImpl>();
			DataContainerImpl data = unsortedList.get(0);
			oneDataList.add(data);
			unsortedList.remove(data);
			for (DataContainerImpl d : unsortedList) {
				if (d.name().equals(data.name())) {
					oneDataList.add(d);
				}
			}
			unsortedList.removeAll(oneDataList);
			groupedByName.add(oneDataList);
		}
		return groupedByName;
	}

	/**
	 * Creates a dataContainer list with three elements, builds a data value table for this list.
	 */
	public static void test() {
		String memory = "memory";
		String processor = "processor";

		DataContainerImpl c11 = new DataContainerImpl(memory, 28, null, null);
		DataContainerImpl c12 = new DataContainerImpl(memory, 56, null, null);
		DataContainerImpl c2 = new DataContainerImpl(processor, 1, null, null);
		ArrayList<DataContainerImpl> dataList = new ArrayList<DataContainerImpl>(
				Arrays.asList(c11, c12, c2));
		ArrayList<ArrayList<DataContainerImpl>> dataTable = getDataValueTable(dataList);
		System.out.println(dataTable);
	}

}
