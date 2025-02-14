package me.neznamy.tab.shared.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.shared.Property;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.cpu.TabFeature;
import me.neznamy.tab.shared.cpu.UsageType;
import me.neznamy.tab.shared.features.types.Loadable;
import me.neznamy.tab.shared.features.types.Refreshable;
import me.neznamy.tab.shared.features.types.event.JoinEventListener;
import me.neznamy.tab.shared.features.types.event.WorldChangeListener;
import me.neznamy.tab.shared.features.types.packet.PlayerInfoPacketListener;
import me.neznamy.tab.shared.packets.IChatBaseComponent;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo.EnumPlayerInfoAction;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo.PlayerInfoData;

/**
 * Feature handler for tablist prefix/name/suffix
 */
public class Playerlist implements JoinEventListener, Loadable, WorldChangeListener, Refreshable {

	private TAB tab;
	private Set<String> usedPlaceholders;
	public List<String> disabledWorlds;
	private boolean antiOverrideNames;
	private boolean antiOverrideTablist;
	private boolean disabling = false;

	public Playerlist(TAB tab) {
		this.tab = tab;
		disabledWorlds = tab.getConfiguration().config.getStringList("disable-features-in-"+tab.getPlatform().getSeparatorType()+"s.tablist-names", Arrays.asList("disabled" + tab.getPlatform().getSeparatorType()));
		antiOverrideNames = tab.getConfiguration().config.getBoolean("anti-override.usernames", true) && tab.getFeatureManager().isFeatureEnabled("injection");
		refreshUsedPlaceholders();
		antiOverrideTablist = tab.getConfiguration().config.getBoolean("anti-override.tablist-names", true) && tab.getFeatureManager().isFeatureEnabled("injection");
		if (antiOverrideTablist) {
			tab.getFeatureManager().registerFeature("playerlist_info", new PlayerInfoPacketListener() {

				@Override
				public TabFeature getFeatureType() {
					return TabFeature.TABLIST_NAMES;
				}

				@Override
				public void onPacketSend(TabPlayer receiver, PacketPlayOutPlayerInfo info) {
					if (disabling) return;
					boolean UPDATE_NAME = info.action == EnumPlayerInfoAction.UPDATE_DISPLAY_NAME;
					boolean ADD = info.action == EnumPlayerInfoAction.ADD_PLAYER;
					if (!UPDATE_NAME && !ADD) return;
					for (PlayerInfoData playerInfoData : info.entries) {
						TabPlayer packetPlayer = tab.getPlayerByTablistUUID(playerInfoData.uniqueId);
						if (packetPlayer != null && !isDisabledWorld(disabledWorlds, packetPlayer.getWorldName())) {
							playerInfoData.displayName = getTabFormat(packetPlayer, receiver);
							//preventing plugins from changing player name as nametag feature would not work correctly
							if (ADD && tab.getFeatureManager().getNameTagFeature() != null && !playerInfoData.name.equals(packetPlayer.getName()) && antiOverrideNames) {
								tab.getErrorManager().printError("A plugin tried to change name of " +  packetPlayer.getName() + " to \"" + playerInfoData.name + "\" for viewer " + receiver.getName(), null, false, tab.getErrorManager().antiOverrideLog);
								playerInfoData.name = packetPlayer.getName();
							}
						}
					}
				}
				
			});
		}
		tab.debug(String.format("Loaded Playerlist feature with parameters disabledWorlds=%s, antiOverrideTablist=%s", disabledWorlds, antiOverrideTablist));
	}

	@Override
	public void load(){
		for (TabPlayer all : tab.getPlayers()) {
			if (isDisabledWorld(disabledWorlds, all.getWorldName())) updateProperties(all);
			refresh(all, true);
		}
	}

	@Override
	public void unload(){
		disabling = true;
		List<PlayerInfoData> updatedPlayers = new ArrayList<PlayerInfoData>();
		for (TabPlayer p : tab.getPlayers()) {
			if (!isDisabledWorld(disabledWorlds, p.getWorldName())) updatedPlayers.add(new PlayerInfoData(p.getUniqueId()));
		}
		for (TabPlayer all : tab.getPlayers()) {
			if (all.getVersion().getMinorVersion() >= 8) all.sendCustomPacket(new PacketPlayOutPlayerInfo(EnumPlayerInfoAction.UPDATE_DISPLAY_NAME, updatedPlayers), getFeatureType());
		}
	}

	@Override
	public void onWorldChange(TabPlayer p, String from, String to) {
		if (isDisabledWorld(disabledWorlds, to) && !isDisabledWorld(disabledWorlds, from)) {
			for (TabPlayer viewer : tab.getPlayers()) {
				if (viewer.getVersion().getMinorVersion() < 8) continue;
				viewer.sendCustomPacket(new PacketPlayOutPlayerInfo(EnumPlayerInfoAction.UPDATE_DISPLAY_NAME, new PlayerInfoData(p.getTablistUUID(), getTabFormat(p, viewer))));
			}
		} else {
			refresh(p, true);
		}
	}

	public IChatBaseComponent getTabFormat(TabPlayer p, TabPlayer viewer) {
		Property prefix = p.getProperty("tabprefix");
		Property name = p.getProperty("customtabname");
		Property suffix = p.getProperty("tabsuffix");
		if (prefix == null || name == null || suffix == null) {
//			tab.errorManager.printError("TabFormat not initialized for " + p.getName());
			return null;
		}
		String format;
		AlignedSuffix alignedSuffix = (AlignedSuffix) tab.getFeatureManager().getFeature("alignedsuffix");
		if (alignedSuffix != null) {
			format = alignedSuffix.formatName(prefix.getFormat(viewer) + name.getFormat(viewer), suffix.getFormat(viewer));
		} else {
			format = prefix.getFormat(viewer) + name.getFormat(viewer) + suffix.getFormat(viewer);
		}
		return IChatBaseComponent.optimizedComponent(format);
	}
	@Override
	public void refresh(TabPlayer refreshed, boolean force) {
		if (isDisabledWorld(disabledWorlds, refreshed.getWorldName())) return;
		boolean refresh;
		if (force) {
			updateProperties(refreshed);
			refresh = true;
		} else {
			boolean prefix = refreshed.getProperty("tabprefix").update();
			boolean name = refreshed.getProperty("customtabname").update();
			boolean suffix = refreshed.getProperty("tabsuffix").update();
			refresh = prefix || name || suffix;
		}
		if (refresh) {
			Property prefix = refreshed.getProperty("tabprefix");
			Property name = refreshed.getProperty("customtabname");
			Property suffix = refreshed.getProperty("tabsuffix");
			for (TabPlayer viewer : tab.getPlayers()) {
				if (viewer.getVersion().getMinorVersion() < 8) continue;
				String format;
				AlignedSuffix alignedSuffix = (AlignedSuffix) tab.getFeatureManager().getFeature("alignedsuffix");
				if (alignedSuffix != null) {
					format = alignedSuffix.formatNameAndUpdateLeader(refreshed, prefix.getFormat(viewer) + name.getFormat(viewer), suffix.getFormat(viewer));
				} else {
					format = prefix.getFormat(viewer) + name.getFormat(viewer) + suffix.getFormat(viewer);
				}
				viewer.sendCustomPacket(new PacketPlayOutPlayerInfo(EnumPlayerInfoAction.UPDATE_DISPLAY_NAME, new PlayerInfoData(refreshed.getTablistUUID(), IChatBaseComponent.optimizedComponent(format))), getFeatureType());
			}
		}
	}
	private void updateProperties(TabPlayer p) {
		p.loadPropertyFromConfig("tabprefix");
		p.loadPropertyFromConfig("customtabname", p.getName());
		p.loadPropertyFromConfig("tabsuffix");
	}

	@Override
	public List<String> getUsedPlaceholders() {
		return new ArrayList<>(usedPlaceholders);
	}

	@Override
	public void onJoin(TabPlayer connectedPlayer) {
		if (isDisabledWorld(disabledWorlds, connectedPlayer.getWorldName())) updateProperties(connectedPlayer);
		Runnable r = () -> {
			refresh(connectedPlayer, true);
			if (connectedPlayer.getVersion().getMinorVersion() < 8) return;
			List<PlayerInfoData> list = new ArrayList<PlayerInfoData>();
			for (TabPlayer all : tab.getPlayers()) {
				if (all == connectedPlayer) continue; //already sent 4 lines above
				list.add(new PlayerInfoData(all.getTablistUUID(), getTabFormat(all, connectedPlayer)));
			}
			connectedPlayer.sendCustomPacket(new PacketPlayOutPlayerInfo(EnumPlayerInfoAction.UPDATE_DISPLAY_NAME, list), getFeatureType());
		};
		r.run();
		//add packet might be sent after tab's refresh packet, resending again when anti-override is disabled
		if (!antiOverrideTablist) tab.getCPUManager().runTaskLater(100, "processing PlayerJoinEvent", getFeatureType(), UsageType.PLAYER_JOIN_EVENT, r);
	}

	@Override
	public void refreshUsedPlaceholders() {
		usedPlaceholders = new HashSet<>(tab.getConfiguration().config.getUsedPlaceholderIdentifiersRecursive("tabprefix", "customtabname", "tabsuffix"));
		for (TabPlayer p : tab.getPlayers()) {
			usedPlaceholders.addAll(tab.getPlaceholderManager().getUsedPlaceholderIdentifiersRecursive(p.getProperty("tabprefix").getCurrentRawValue(),
					p.getProperty("customtabname").getCurrentRawValue(), p.getProperty("tabsuffix").getCurrentRawValue()));
		}
	}

	@Override
	public TabFeature getFeatureType() {
		return TabFeature.TABLIST_NAMES;
	}
}