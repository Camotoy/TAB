package me.neznamy.tab.platforms.bukkit.nms.datawatcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.neznamy.tab.platforms.bukkit.nms.NMSStorage;

/**
 * A class representing the n.m.s.DataWatcher class to make work with it much easier
 */
public class DataWatcher {

	//datawatcher data
	private Map<Integer, DataWatcherItem> dataValues = new HashMap<Integer, DataWatcherItem>();
	
	//a helper for easier data write
	private DataWatcherHelper helper = new DataWatcherHelper(this);

	/**
	 * Sets value into data values
	 * @param type - type of value
	 * @param value - value
	 */
	public void setValue(DataWatcherObject type, Object value){
		dataValues.put(type.position, new DataWatcherItem(type, value));
	}

	/**
	 * Removes value by position
	 * @param position - position of value to remove
	 */
	public void removeValue(int position) {
		dataValues.remove(position);
	}

	/**
	 * Returns item with given position
	 * @param position - position of item
	 * @return item or null if not set
	 */
	public DataWatcherItem getItem(int position) {
		return dataValues.get(position);
	}

	/**
	 * Returns helper created by this instance
	 * @return data write helper
	 */
	public DataWatcherHelper helper() {
		return helper;
	}

	/**
	 * Converts the class into an instance of NMS.DataWatcher
	 * @return an instance of NMS.DataWatcher with same data
	 * @throws Exception - if something fails
	 */
	public Object toNMS() throws Exception {
		NMSStorage nms = NMSStorage.getInstance();
		Object nmsWatcher;
		if (nms.newDataWatcher.getParameterCount() == 1) {
			nmsWatcher = nms.newDataWatcher.newInstance(new Object[] {null});
		} else {
			nmsWatcher = nms.newDataWatcher.newInstance();
		}
		for (DataWatcherItem item : dataValues.values()) {
			Object position;
			if (nms.minorVersion >= 9) {
				position = nms.newDataWatcherObject.newInstance(item.type.position, item.type.classType);
			} else {
				position = item.type.position;
			}
			nms.DataWatcher_REGISTER.invoke(nmsWatcher, position, item.value);
		}
		return nmsWatcher;
	}
	
	/**
	 * Reads NMS data watcher and returns and instance of this class with same data
	 * @param nmsWatcher - NMS datawatcher to read
	 * @return an instance of this class with same values
	 * @throws Exception - if something fails
	 */
	@SuppressWarnings("unchecked")
	public static DataWatcher fromNMS(Object nmsWatcher) throws Exception{
		DataWatcher watcher = new DataWatcher();
		List<Object> items = (List<Object>) nmsWatcher.getClass().getMethod("c").invoke(nmsWatcher);
		if (items != null) {
			for (Object watchableObject : items) {
				DataWatcherItem w = DataWatcherItem.fromNMS(watchableObject);
				watcher.setValue(w.type, w.value);
			}
		}
		return watcher;
	}
}