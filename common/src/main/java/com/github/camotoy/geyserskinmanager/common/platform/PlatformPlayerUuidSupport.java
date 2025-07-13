package com.github.camotoy.geyserskinmanager.common.platform;

import java.util.UUID;

/**
 * @param <T> 此平台的玩家类 - BungeeCord的ProxiedPlayer，Spigot的Player，等等。
 */
public interface PlatformPlayerUuidSupport<T> {
    UUID getUUID(T player);
}
