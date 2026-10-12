package me.adda.enhanced_falling_trees.utils;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.adda.enhanced_falling_trees.api.TreeRegistry;
import me.adda.enhanced_falling_trees.api.TreeType;
import me.adda.enhanced_falling_trees.config.CommonConfig;
import me.adda.enhanced_falling_trees.config.FallingTreesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.Set;

public class TreeBreakingUtils {
    private static final Long2ObjectMap<TreeCacheEntry> treeCache = new Long2ObjectOpenHashMap<>();
    private static final long CACHE_LIFETIME = 2000;
    private static final int MAX_CACHE_SIZE = 100;

    private record TreeCacheEntry(float multiplier, long timestamp, String playerUUID) {}

    public static Optional<Float> getCachedMultiplier(BlockState state, Player player, Level level, BlockPos pos) {
        long posKey = pos.asLong();
        TreeCacheEntry cached = treeCache.get(posKey);
        long currentTime = System.currentTimeMillis();

        if (cached != null &&
                currentTime - cached.timestamp < CACHE_LIFETIME &&
                cached.playerUUID.equals(player.getStringUUID())) {
            return Optional.of(cached.multiplier);
        }

        if (treeCache.size() > MAX_CACHE_SIZE || cached != null) {
            cleanCache(currentTime);
        }

        return calculateMultiplier(state, player, level, pos).map(multiplier -> {
            treeCache.put(posKey, new TreeCacheEntry(
                    multiplier,
                    currentTime,
                    player.getStringUUID()
            ));
            return multiplier;
        });
    }

    private static Optional<Float> calculateMultiplier(BlockState state, Player player, Level level, BlockPos pos) {
        var config = FallingTreesConfig.getCommonConfig();
        if (!config.treeBreaking.enableBreakingSpeedModification) {
            return Optional.empty();
        }

        Optional<TreeType> treeType = TreeRegistry.getTreeType(state);
        if (treeType.isEmpty()) {
            return Optional.empty();
        }

        TreeType type = treeType.get();
        Set<BlockPos> treeBlockPos = type.blockGatheringAlgorithm(pos, level);

        if (treeBlockPos.isEmpty() || !type.allowedToFall(player, level, pos, config.limitations.treeFallRequirements)) {
            return Optional.empty();
        }

        long treeHeight = treeBlockPos.stream()
                .filter(blockPos -> blockPos.getX() == pos.getX() &&
                        blockPos.getZ() == pos.getZ() &&
                        type.baseBlockCheck(level.getBlockState(blockPos)))
                .count();

        float toolMultiplier = getToolMultiplier(player, config);
        float heightMultiplier = calculateHeightMultiplier(treeHeight);

        return Optional.of(toolMultiplier * heightMultiplier);
    }

    private static void cleanCache(long currentTime) {
        LongArrayList expiredKeys = new LongArrayList();
        for (Long2ObjectMap.Entry<TreeCacheEntry> entry : treeCache.long2ObjectEntrySet()) {
            if (currentTime - entry.getValue().timestamp >= CACHE_LIFETIME) {
                expiredKeys.add(entry.getLongKey());
            }
        }
        for (long key : expiredKeys) {
            treeCache.remove(key);
        }
    }

    private static float getToolMultiplier(Player player, CommonConfig config) {
        boolean isAxe = config.filter.tool.isValid(player.getMainHandItem().getItem());

        return isAxe ?
                config.treeBreaking.axeSpeedMultiplier / 100.0f :
                config.treeBreaking.noAxeSpeedMultiplier / 100.0f;
    }

    private static float calculateHeightMultiplier(long height) {
        int baseHeight = 5;
        float heightImpact = 0.5f;

        if (height <= baseHeight) {
            return 1.0f;
        }
        float heightFactor = 1.0f + ((height - baseHeight) * heightImpact);
        return 1.0f / heightFactor;
    }
}