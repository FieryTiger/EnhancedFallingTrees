package me.adda.enhanced_falling_trees.config.common;

import me.shedaniel.autoconfig.annotation.ConfigEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FilterConfig {
	private static final TagKey<Item> PAXELS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/paxels"));

	@ConfigEntry.Gui.CollapsibleObject
	public FilterBlock log = new FilterBlock(List.of(BlockTags.LOGS), new ArrayList<>(), new ArrayList<>());
	@ConfigEntry.Gui.CollapsibleObject
	public FilterBlock leaves = new FilterBlock(List.of(BlockTags.LEAVES), new ArrayList<>(), new ArrayList<>());
	@ConfigEntry.Gui.CollapsibleObject
	public FilterItem tool = new FilterItem(List.of(ItemTags.AXES, PAXELS), new ArrayList<>(), new ArrayList<>());

	public static class FilterBlock {
		public List<String> whitelistedBlockTags;
		public List<String> whitelistedBlocks;
		public List<String> blacklistedBlocks;

		public FilterBlock(List<TagKey<Block>> whitelistedBlockTags, List<Block> whitelistedBlocks, List<Block> blacklistedBlocks) {
			this.whitelistedBlockTags = whitelistedBlockTags.stream()
					.map(blockTagKey -> blockTagKey.location().toString())
					.toList();

			this.whitelistedBlocks = whitelistedBlocks.stream()
					.map(block -> BuiltInRegistries.BLOCK.getKey(block).toString())
					.toList();

			this.blacklistedBlocks = blacklistedBlocks.stream()
					.map(block -> BuiltInRegistries.BLOCK.getKey(block).toString())
					.toList();
		}

		public FilterBlock() {
			// Пустой конструктор для autoconfig
			this.whitelistedBlockTags = new ArrayList<>();
			this.whitelistedBlocks = new ArrayList<>();
			this.blacklistedBlocks = new ArrayList<>();
		}

		public boolean isValid(Block block) {
			ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
			String blockName = blockId.toString();

			if (blacklistedBlocks.contains(blockName))
				return false;

			BlockState state = block.defaultBlockState();
			boolean matchesTag = state.getTags()
					.anyMatch(tag -> whitelistedBlockTags.contains(tag.location().toString()));

			boolean inWhitelist = whitelistedBlocks.contains(blockName);

			return matchesTag || inWhitelist;
		}
	}

	public static class FilterItem {
		public List<String> whitelistedItemTags;
		public List<String> whitelistedItems;
		public List<String> blacklistedItems;

		public FilterItem(List<TagKey<Item>> whitelistedItemTags, List<Item> whitelistedItems, List<Item> blacklistedItems) {
			this.whitelistedItemTags = whitelistedItemTags.stream()
					.map(itemTagKey -> itemTagKey.location().toString())
					.collect(Collectors.toCollection(ArrayList::new));

			this.whitelistedItems = whitelistedItems.stream()
					.map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
					.collect(Collectors.toCollection(ArrayList::new));

			this.blacklistedItems = blacklistedItems.stream()
					.map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
					.collect(Collectors.toCollection(ArrayList::new));
		}

		public FilterItem() {
			// Пустой конструктор для autoconfig
			this.whitelistedItemTags = new ArrayList<>();
			this.whitelistedItems = new ArrayList<>();
			this.blacklistedItems = new ArrayList<>();
		}

		public boolean isValid(Item item) {
			ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
			String itemName = itemId.toString();

			if (blacklistedItems.contains(itemName))
				return false;

			boolean matchesTag = BuiltInRegistries.ITEM.wrapAsHolder(item).tags()
					.anyMatch(tag -> whitelistedItemTags.contains(tag.location().toString()));

			boolean inWhitelist = whitelistedItems.contains(itemName);

			return matchesTag || inWhitelist;
		}
	}
}
