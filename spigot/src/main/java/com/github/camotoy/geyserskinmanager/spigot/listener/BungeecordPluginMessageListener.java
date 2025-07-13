package com.github.camotoy.geyserskinmanager.spigot.listener;

import com.github.camotoy.geyserskinmanager.common.Constants;
import com.github.camotoy.geyserskinmanager.common.SkinEntry;
import com.github.camotoy.geyserskinmanager.spigot.GeyserSkinManager;
import com.github.camotoy.geyserskinmanager.spigot.SpigotSkinApplier;
import com.github.camotoy.geyserskinmanager.spigot.profile.GameProfileWrapper;
import com.github.camotoy.geyserskinmanager.spigot.profile.MinecraftProfileWrapper;
import com.github.camotoy.geyserskinmanager.spigot.profile.PaperProfileWrapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class BungeecordPluginMessageListener implements Listener, PluginMessageListener {
    private final GeyserSkinManager plugin;
    private final Function<Player, MinecraftProfileWrapper> getProfileFunction;
    private final SpigotSkinApplier skinApplier;

    /**
     * 如果插件消息在玩家加入之前收到，信息会存储在这里。
     */
    private final Cache<UUID, SkinEntry> skinEntryCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .build();

    public BungeecordPluginMessageListener(GeyserSkinManager plugin) {
        this.plugin = plugin;
        this.skinApplier = new SpigotSkinApplier(plugin);
        this.getProfileFunction = (PaperLib.isPaper() && PaperLib.isVersion(12, 2)) ?
                 PaperProfileWrapper::from : GameProfileWrapper::from;

        Bukkit.getPluginManager().registerEvents(this, this.plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        SkinEntry skinEntry = skinEntryCache.getIfPresent(event.getPlayer().getUniqueId());
        if (skinEntry != null) {
            skinApplier.setSkin(getProfileFunction.apply(event.getPlayer()), event.getPlayer(), skinEntry);
        }
    }

    @Override
    public void onPluginMessageReceived(@Nonnull String channel, @Nonnull Player player, @Nonnull byte[] message) {
        if (!channel.equals(Constants.SKIN_PLUGIN_MESSAGE_NAME)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            int version = in.readInt();
            if (version != Constants.SKIN_PLUGIN_MESSAGE_VERSION) {
                plugin.getLogger().warning("收到了版本无效的插件消息！请确保BungeeCord和后端服务器上的GeyserSkinManager都已更新！");
                return;
            }
            UUID uuid = new UUID(in.readLong(), in.readLong());
            String value = in.readUTF();
            String signature = in.readUTF();
            SkinEntry skinEntry = new SkinEntry(value, signature);

            Player bedrockPlayer = Bukkit.getPlayer(uuid);
            if (bedrockPlayer == null) {
                // 等待他们正式加入
                skinEntryCache.put(uuid, skinEntry);
                return;
            }
            skinApplier.setSkin(getProfileFunction.apply(bedrockPlayer), bedrockPlayer, skinEntry);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
