package dev.efnilite.vilib.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

public class SkullSetter {

    private static boolean isPaper;
    private static Method getPlayerProfileMethod;
    private static Method hasTexturesMethod;
    private static Method setPlayerProfileMethod;

    private static Class<?> gameProfileClass;
    private static Class<?> propertyClass;
    private static Constructor<?> gameProfileConstructor;
    private static Constructor<?> propertyConstructor;
    private static Field skullProfileField;
    private static Method propertyValueMethod; 

    private static Constructor<?> resolvableProfileConstructor;

    static {
        try {
            Class<?> playerProfileClass = Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            getPlayerProfileMethod = Player.class.getDeclaredMethod("getPlayerProfile");
            hasTexturesMethod = playerProfileClass.getDeclaredMethod("hasTextures");
            setPlayerProfileMethod = SkullMeta.class.getDeclaredMethod("setPlayerProfile", playerProfileClass);
            isPaper = true;
        } catch (Exception ex) {
            isPaper = false;
        }

        try {
            gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            
            gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            propertyConstructor = propertyClass.getConstructor(String.class, String.class);

            try {
                propertyValueMethod = propertyClass.getMethod("value");
            } catch (NoSuchMethodException e) {
                propertyValueMethod = propertyClass.getMethod("getValue");
            }

            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            Class<?> craftMetaSkull = Class.forName(packageName + ".inventory.CraftMetaSkull");
            skullProfileField = craftMetaSkull.getDeclaredField("profile");
            skullProfileField.setAccessible(true);

            try {
                Class<?> resolvableProfileClass = Class.forName("net.minecraft.world.item.component.ResolvableProfile");
                resolvableProfileConstructor = resolvableProfileClass.getConstructor(gameProfileClass);
            } catch (ClassNotFoundException ignored) {
            }
            
        } catch (Exception e) {
            Bukkit.getLogger().warning("[vilib] Failed to initialize reflection for skins: " + e.getMessage());
        }
    }

    public static void setPlayerHead(OfflinePlayer player, SkullMeta meta) {
        if (!isPaper) {
            meta.setOwningPlayer(player);
            return;
        }
        try {
            Object playerProfile = getPlayerProfileMethod.invoke(player);
            boolean hasTexture = (boolean) hasTexturesMethod.invoke(playerProfile);
            if (hasTexture) {
                setPlayerProfileMethod.invoke(meta, playerProfile);
            } else {
                meta.setOwningPlayer(player);
            }
        } catch (Exception ex) {
            meta.setOwningPlayer(player);
        }
    }

    public static String getTexture(Player player) {
        try {
            Object craftPlayer = player;
            Method getHandle = craftPlayer.getClass().getMethod("getHandle");
            Object entityPlayer = getHandle.invoke(craftPlayer);

            Method getGameProfile = entityPlayer.getClass().getMethod("getGameProfile");
            Object gameProfile = getGameProfile.invoke(entityPlayer);

            Method getProperties = gameProfile.getClass().getMethod("getProperties");
            Object propertyMap = getProperties.invoke(gameProfile);

            Method get = propertyMap.getClass().getMethod("get", Object.class);
            Collection<?> textures = (Collection<?>) get.invoke(propertyMap, "textures");

            if (textures != null && !textures.isEmpty()) {
                Object property = textures.iterator().next();
                return (String) propertyValueMethod.invoke(property);
            }
        } catch (Exception e) {
        }
        return null;
    }

    public static void applyTexture(SkullMeta meta, String texture) {
        if (texture == null || texture.isEmpty() || gameProfileConstructor == null) {
            return;
        }

        try {
            Object profile = gameProfileConstructor.newInstance(UUID.randomUUID(), "custom_skin");
            Object property = propertyConstructor.newInstance("textures", texture);

            Method getProperties = gameProfileClass.getMethod("getProperties");
            Object propertyMap = getProperties.invoke(profile);
            Method put = propertyMap.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(propertyMap, "textures", property);

            Object valueToSet = profile;

            if (resolvableProfileConstructor != null) {
                valueToSet = resolvableProfileConstructor.newInstance(profile);
            }

            skullProfileField.set(meta, valueToSet);

        } catch (Exception e) {
        }
    }
}