package me.adda.enhanced_falling_trees.config;

import me.adda.enhanced_falling_trees.FallingTrees;
import me.adda.enhanced_falling_trees.api.platform.PlatformServices;
import me.adda.enhanced_falling_trees.network.ConfigPacket;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

public class FallingTreesConfig {
	public final ConfigHolder<ClientConfig> clientConfigHolder;
	public final ConfigHolder<CommonConfig> commonConfigHolder;
	private CommonConfig commonConfig;

	public FallingTreesConfig() {
		clientConfigHolder = AutoConfig.register(ClientConfig.class, GsonConfigSerializer::new);
		commonConfigHolder = AutoConfig.register(CommonConfig.class, GsonConfigSerializer::new);

		if (PlatformServices.getPlatform().isClient()) {
			clientConfigHolder.registerSaveListener(this::saveConfig);
			commonConfigHolder.registerSaveListener(this::saveCommonConfig);
		}
	}

	private InteractionResult saveConfig(ConfigHolder<ClientConfig> configHolder, ClientConfig config) {
		if (Minecraft.getInstance().level != null) {
			ConfigPacket.sendToServer();
		}

		return InteractionResult.PASS;
	}

	private InteractionResult saveCommonConfig(ConfigHolder<CommonConfig> configHolder, CommonConfig config) {
		// In singleplayer the copy synced from the integrated server would keep the old values until rejoining
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			this.commonConfig = null;
		}

		return InteractionResult.PASS;
	}

	public static ClientConfig getClientConfig() {
		return FallingTrees.CONFIG.clientConfigHolder.getConfig();
	}

	public static CommonConfig getCommonConfig() {
		if (FallingTrees.CONFIG.commonConfig != null)
			return FallingTrees.CONFIG.commonConfig;
		return FallingTrees.CONFIG.commonConfigHolder.getConfig();
	}

	public void setCommonConfig(CommonConfig commonConfig) {
		this.commonConfig = commonConfig;
	}
}
