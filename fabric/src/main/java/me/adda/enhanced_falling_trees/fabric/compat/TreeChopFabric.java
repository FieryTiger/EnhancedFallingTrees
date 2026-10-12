package me.adda.enhanced_falling_trees.fabric.compat;

import ht.treechop.api.TreeChopEvents;
import me.adda.enhanced_falling_trees.compat.TreeChop;

import java.util.stream.Stream;

public final class TreeChopFabric {
    private TreeChopFabric() {
    }

    public static void register() {
        TreeChop.markLoaded();
        TreeChopEvents.BEFORE_FELL.register((level, player, choppedPos, fellData) -> !TreeChop.fell(
                level, player, choppedPos,
                fellData.getTree().streamLogs(),
                fellData.getBreakLeaves() ? fellData.getTree().streamLeaves() : Stream.empty()
        ));
    }
}