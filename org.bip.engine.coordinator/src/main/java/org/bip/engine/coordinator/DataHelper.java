package org.bip.engine.coordinator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.bip.api.DataValue;

public class DataHelper {

	/**
	 * OLD METHOD, SHOULD BE ELIMINATED (hence, no good comments here)
	 * Given an sorted list of data containers and information about multiple usage of the same data,
	 * the method returns a list of maps name-value, necessary for port execution
	 * 
	 * @param containerList
	 *            the container list
	 * @param dataCount
	 *            a map containing names of those data that are needed more than
	 *            once, and their required count
	 * @return the array list
	 */
	public static ArrayList<Map<String, Object>> createDataTable(
			ArrayList<ArrayList<DataContainerImpl>> containerList, HashMap<String, Integer> dataCount) {

		ArrayList<Map<String, Object>> dataTable = new ArrayList<Map<String, Object>>();
		for (ArrayList<DataContainerImpl> container : containerList) {
			Map<String, Object> row = new Hashtable<String, Object>();
			for (DataContainerImpl dc : container) {
				row.put(dc.name(), dc.value());
			}
			dataTable.add(row);
		}
		return dataTable;
	}

	/**
	 * Given an unsorted list of data containers and information about multiple usage of the same data,
	 * the method returns a list of tuples consisting of name-value data pairs, necessary for port execution
	 * 
	 * @param dataList
	 * 			list of unsorted data containers
	 * @param dataCount
	 * 			 a map containing the names of all data which are required more
	 *            than once, where the value for the name is the number of
	 *            additional(!) times that the specific data is needed. (i.e. if
	 *            data is needed twice, the value will be 1).
	 * @return a list of rows of dataValues (name-value pairs), where each row provides all the data needed by a port
	 */
	public static ArrayList<ArrayList<DataValue>> createDataValueTable(
					ArrayList<DataContainerImpl> dataList,	HashMap<String, Integer> dataCount) {
		
		ArrayList<ArrayList<DataContainerImpl>> containerList = getDataValueTable(dataList, dataCount);
		ArrayList<ArrayList<DataValue>> dataValues = new ArrayList<ArrayList<DataValue>>();
		for (ArrayList<DataContainerImpl> row : containerList) {
			ArrayList<DataValue> dataValueRow = new ArrayList<DataValue>();
			for (DataContainerImpl container : row) {
				dataValueRow.add(new DataValue(container.name(), container.value()));
			}
			dataValues.add(dataValueRow);
		}
		return dataValues;
	}

	/**
	 * Given an unsorted list of data containers, creates a grouped list of data
	 * containers, where each sub-list is a permutation containing a value for each data request 
	 * (one for each data name, or several for those data names which are required more than once)
	 * 
	 * @param unsortedDataList
	 *            list of unsorted data containers
	 * @param dataCount
	 * 			 a map containing the names of all data which are required more
	 *            than once, where the value for the name is the number of
	 *            additional(!) times that the specific data is needed. (i.e. if
	 *            data is needed twice, the value will be 1).
	 * @return a list of data containers grouped into sub-lists, 
	 * 			each sub-list having a different tuple of data values, 
	 * 			and the size of sublist must be equal to the number of required data 
	 */
	public static ArrayList<ArrayList<DataContainerImpl>> getDataValueTable(
			ArrayList<DataContainerImpl> unsortedDataList, HashMap<String, Integer> dataCount) {
		ArrayList<ArrayList<DataContainerImpl>> result = new ArrayList<ArrayList<DataContainerImpl>>();

		if (unsortedDataList == null || unsortedDataList.isEmpty()) {
			return result;
		}
		// the data is now grouped by the name
		ArrayList<ArrayList<DataContainerImpl>> sortedList = dataGroupedByName(
				unsortedDataList, dataCount);
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
	 * name. If one data must be used several times, as indicated by dataCount
	 * parameter, the resulting list is augmented with repetitive lists.
	 * 
	 * @param unsortedList
	 *            the list of all data acquired from all components
	 * @param dataCount
	 *            a map containing the names of all data which are required more
	 *            than once, where the value for the name is the number of
	 *            additional(!) times that the specific data is needed. (i.e. if
	 *            data is needed twice, the value will be 1).
	 * @return list of data lists grouped by name
	 */
	private static ArrayList<ArrayList<DataContainerImpl>> dataGroupedByName(
			ArrayList<DataContainerImpl> unsortedList,
			HashMap<String, Integer> dataCount) {
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
			// if the data grouped last is needed by a component more than once,
			// add the same list several times
			if (dataCount.containsKey(data.name())) {
				for (int i = 0; i < dataCount.get(data.name()); i++) {
					groupedByName.add(oneDataList);
				}
			}
			groupedByName.add(oneDataList);
		}
		return groupedByName;
	}

	/**
	 * Creates a dataContainer list with three elements, two out of which are for the same data, which is required twice.
	 * Builds a data value table for this list.
	 */
	public static void test() {
		String memory = "memory";
		String processor = "processor";

		DataContainerImpl c11 = new DataContainerImpl(memory, 28, null, null);
		DataContainerImpl c12 = new DataContainerImpl(memory, 56, null, null);
		DataContainerImpl c2 = new DataContainerImpl(processor, 1, null, null);
		ArrayList<DataContainerImpl> dataList = new ArrayList<DataContainerImpl>(
				Arrays.asList(c11, c12, c2));
		HashMap<String, Integer> dataCount = new HashMap<String, Integer>();
		dataCount.put(memory, 1);
		ArrayList<ArrayList<DataContainerImpl>> dataTable = getDataValueTable(
				dataList, dataCount);
		System.out.println(dataTable);
	}

}
