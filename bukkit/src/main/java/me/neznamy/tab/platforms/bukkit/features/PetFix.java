package me.neznamy.tab.platforms.bukkit.features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.platforms.bukkit.nms.NMSStorage;
import me.neznamy.tab.platforms.bukkit.nms.datawatcher.DataWatcher;
import me.neznamy.tab.platforms.bukkit.nms.datawatcher.DataWatcherItem;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.cpu.TabFeature;
import me.neznamy.tab.shared.features.types.event.QuitEventListener;
import me.neznamy.tab.shared.features.types.packet.RawPacketListener;

/**
 * A feature to disable minecraft 1.9+ feature making tamed animals with custom names copy nametag properties of their owner
 * This is achieved by listening to entity spawn (<1.15) / entity metadata packets and removing owner field from the datawatcher list
 * Since 1.16 this results in client sending entity use packet twice, so we must cancel the 2nd one to prevent double toggle
 */
public class PetFix implements RawPacketListener, QuitEventListener {

	//datawatcher position of pet owner field
	private int PET_OWNER_POSITION;

	//logger of last interacts to prevent feature not working on 1.16
	private Map<String, Long> lastInteractFix = new HashMap<String, Long>();
	
	//nms storage
	private NMSStorage nms;
	
	/**
	 * Constructs new instance with given parameter
	 * @param nms
	 */
	public PetFix(NMSStorage nms) {
		this.nms = nms;
		PET_OWNER_POSITION = getPetOwnerPosition();
		TAB.getInstance().debug("Loaded PetFix feature");
	}
	
	/**
	 * Returns position of pet owner field based on server version
	 * @return position of pet owner field based on server version
	 */
	private int getPetOwnerPosition() {
		if (nms.minorVersion >= 15) {
			//1.15.x, 1.16.x
			return 17;
		} else if (nms.minorVersion >= 14) {
			//1.14.x
			return 16;
		} else if (nms.minorVersion >= 10) {
			//1.10.x - 1.13.x
			return 14;
		} else {
			//1.9.x
			return 13;
		}
	}
	
	/**
	 * Cancels a packet if previous one arrived with no delay to prevent double toggle on 1.16
	 */
	@Override
	public Object onPacketReceive(TabPlayer sender, Object packet) throws Throwable {
		if (nms.PacketPlayInUseEntity.isInstance(packet)) {
			if (lastInteractFix.containsKey(sender.getName()) && (System.currentTimeMillis() - lastInteractFix.get(sender.getName()) < 5)) {
				//last interact packet was sent right now, cancelling to prevent double-toggle due to this feature enabled
				return null;
			}
			if (nms.PacketPlayInUseEntity_ACTION.get(packet).toString().equals("INTERACT")) {
				//this is the first packet, saving player so the next packet can be cancelled
				lastInteractFix.put(sender.getName(), System.currentTimeMillis());
			}
		}
		return packet;
	}
	
	/**
	 * Removes pet owner field from datawatcher
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void onPacketSend(TabPlayer receiver, Object packet) throws Throwable {
		if (nms.PacketPlayOutEntityMetadata.isInstance(packet)) {
			List<Object> items = (List<Object>) nms.PacketPlayOutEntityMetadata_LIST.get(packet);
			if (items == null) return;
			List<Object> newList = new ArrayList<Object>();
			for (Object item : items) {
				if (nms.DataWatcherObject_SLOT.getInt(nms.DataWatcherItem_TYPE.get(item)) == PET_OWNER_POSITION) {
					Object value = nms.DataWatcherItem_VALUE.get(item);
					if (value.getClass().getSimpleName().equals("Optional")) continue;
				}
				newList.add(item);
			}
			nms.PacketPlayOutEntityMetadata_LIST.set(packet, newList);
		}
		//<1.15
		if (nms.PacketPlayOutSpawnEntityLiving.isInstance(packet) && nms.PacketPlayOutSpawnEntityLiving_DATAWATCHER != null) {
			DataWatcher watcher = DataWatcher.fromNMS(nms.PacketPlayOutSpawnEntityLiving_DATAWATCHER.get(packet));
			DataWatcherItem petOwner = watcher.getItem(PET_OWNER_POSITION);
			if (petOwner != null && petOwner.value.getClass().getSimpleName().equals("Optional")) {
				watcher.removeValue(PET_OWNER_POSITION);
				nms.PacketPlayOutSpawnEntityLiving_DATAWATCHER.set(packet, watcher.toNMS());
			}
		}
	}

	@Override
	public TabFeature getFeatureType() {
		return TabFeature.PET_NAME_FIX;
	}

	/**
	 * Removing player data to not have a memory leak
	 */
	@Override
	public void onQuit(TabPlayer disconnectedPlayer) {
		lastInteractFix.remove(disconnectedPlayer.getName());
	}
}