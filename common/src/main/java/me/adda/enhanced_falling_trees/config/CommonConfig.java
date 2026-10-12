package me.adda.enhanced_falling_trees.config;

import me.adda.enhanced_falling_trees.FallingTrees;
import me.adda.enhanced_falling_trees.config.common.FeaturesConfig;
import me.adda.enhanced_falling_trees.config.common.FilterConfig;
import me.adda.enhanced_falling_trees.config.common.LimitationsConfig;
import me.adda.enhanced_falling_trees.config.common.PhysicsConfig;
import me.adda.enhanced_falling_trees.config.common.TreeBreakingConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = FallingTrees.MOD_ID + "_common")
public class CommonConfig implements ConfigData {
	@ConfigEntry.Gui.PrefixText
	public boolean isCrouchMiningAllowed = true;
	public boolean multiplyToolDamage = true;
	public boolean multiplyFoodExhaustion = true;
	@ConfigEntry.Gui.PrefixText
	public float leafParticleLifeTimeLength = 1;
	public int leafParticleCount = 5;
	@ConfigEntry.Gui.PrefixText
	public float treeLifetimeLength = 4;
	public float cactusLifetimeLength = 2.5f;
	public float bambooLifetimeLength = 2;
	public float chorusLifetimeLength = 4;

	@ConfigEntry.Category("filter")
	@ConfigEntry.Gui.TransitiveObject
	public FilterConfig filter = new FilterConfig();

	@ConfigEntry.Category("limitations")
	@ConfigEntry.Gui.TransitiveObject
	public LimitationsConfig limitations = new LimitationsConfig();

	@ConfigEntry.Category("features")
	@ConfigEntry.Gui.TransitiveObject
	public FeaturesConfig features = new FeaturesConfig();

	@ConfigEntry.Category("physics")
	@ConfigEntry.Gui.TransitiveObject
	public PhysicsConfig physics = new PhysicsConfig();

	@ConfigEntry.Category("tree_breaking")
	@ConfigEntry.Gui.TransitiveObject
	public TreeBreakingConfig treeBreaking = new TreeBreakingConfig();

}
