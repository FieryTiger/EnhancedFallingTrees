package me.adda.enhanced_falling_trees.neoforge.compat;

import ht.treechop.api.ChopEvent;
import me.adda.enhanced_falling_trees.compat.TreeChop;
import net.neoforged.neoforge.common.NeoForge;

import java.util.stream.Stream;

public final class TreeChopNeoForge {
    private TreeChopNeoForge() {
    }

    public static void register() {
        TreeChop.markLoaded();
        NeoForge.EVENT_BUS.addListener((ChopEvent.BeforeFellEvent event) -> {
            boolean handled = TreeChop.fell(
                    event.getLevel(), event.getPlayer(), event.getChoppedBlockPos(),
                    event.getFellData().getTree().streamLogs(),
                    event.getFellData().getBreakLeaves() ? event.getFellData().getTree().streamLeaves() : Stream.empty()
            );
            if (handled) event.setCanceled(true);
        });
    }
}