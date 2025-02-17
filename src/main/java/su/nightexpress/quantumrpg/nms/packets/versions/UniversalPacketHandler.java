package su.nightexpress.quantumrpg.nms.packets.versions;

import mc.promcteam.engine.NexEngine;
import mc.promcteam.engine.hooks.Hooks;
import mc.promcteam.engine.nms.packets.IPacketHandler;
import mc.promcteam.engine.nms.packets.events.EnginePlayerPacketEvent;
import mc.promcteam.engine.nms.packets.events.EngineServerPacketEvent;
import mc.promcteam.engine.utils.ItemUT;
import mc.promcteam.engine.utils.Reflex;
import mc.promcteam.engine.utils.reflection.ReflectionManager;
import mc.promcteam.engine.utils.reflection.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.quantumrpg.QuantumRPG;
import su.nightexpress.quantumrpg.api.event.EntityEquipmentChangeEvent;
import su.nightexpress.quantumrpg.config.EngineCfg;
import su.nightexpress.quantumrpg.data.api.RPGUser;
import su.nightexpress.quantumrpg.data.api.UserEntityNamesMode;
import su.nightexpress.quantumrpg.data.api.UserProfile;
import su.nightexpress.quantumrpg.manager.EntityManager;
import su.nightexpress.quantumrpg.modules.list.itemhints.ItemHintsManager;
import su.nightexpress.quantumrpg.nms.packets.PacketManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public class UniversalPacketHandler implements IPacketHandler {

    protected QuantumRPG plugin;
    protected ReflectionUtil reflectionUtil;

    public UniversalPacketHandler(@NotNull QuantumRPG plugin) {
        this.plugin = plugin;
        reflectionUtil = ReflectionManager.getReflectionUtil();
    }

    @Override
    public void managePlayerPacket(@NotNull EnginePlayerPacketEvent e) {
        Class playoutParticles        = Reflex.getNMSClass("PacketPlayOutWorldParticles");
        Class playoutUpdateAttributes = Reflex.getNMSClass("PacketPlayOutUpdateAttributes");
        Class playoutEntityMetadata   = Reflex.getNMSClass("PacketPlayOutEntityMetadata");
        Class playOutEntityEquipment  = Reflex.getNMSClass("PacketPlayOutEntityEquipment");

        Object packet = e.getPacket();

        if (EngineCfg.PACKETS_REDUCE_COMBAT_PARTICLES && playoutParticles.isInstance(packet)) {
            this.manageDamageParticle(e, packet);
            return;
        }

        if (playoutUpdateAttributes.isInstance(packet)) {
            this.manageEquipmentChanges(e, packet);
            return;
        }
        if (playoutEntityMetadata.isInstance(packet)) {
            this.manageEntityNames(e, packet);
            return;
        }
        if (playOutEntityEquipment.isInstance(packet)) {
            this.managePlayerHelmet(e, packet);
            return;
        }
    }

    @Override
    public void manageServerPacket(@NotNull EngineServerPacketEvent e) {
    }

    public void manageEquipmentChanges(@NotNull EnginePlayerPacketEvent e, @NotNull Object packet) {
        Class playoutUpdateAttributes = Reflex.getNMSClass("PacketPlayOutUpdateAttributes");
        Class craftServerClass        = Reflex.getCraftClass("CraftServer");
        Class nmsEntityClass          = Reflex.getNMSClass("Entity");
        Class worldServerClass        = Reflex.getNMSClass("WorldServer");

        Object equip = playoutUpdateAttributes.cast(packet);

        Integer entityId = (Integer) Reflex.getFieldValue(equip, "a");
        if (entityId == null) return;

        Object server    = craftServerClass.cast(Bukkit.getServer());
        Object nmsEntity = null;

        Object dedicatedServer = Reflex.invokeMethod(
                Reflex.getMethod(craftServerClass, "getServer"),
                server
        );

        Iterable<?> worlds = (Iterable<?>) Reflex.invokeMethod(
                Reflex.getMethod(dedicatedServer.getClass(), "getWorlds"),
                dedicatedServer
        );

        Method getEntity = Reflex.getMethod(worldServerClass, "getEntity", int.class);
        for (Object worldServer : worlds) {
            nmsEntity = Reflex.invokeMethod(getEntity, worldServer, entityId.intValue());
            if (nmsEntity != null) {
                break;
            }
        }

        if (nmsEntity == null) return;


        Method getUniqueId = Reflex.getMethod(nmsEntityClass, "getUniqueID");

        Entity bukkitEntity = NexEngine.get().getServer().getEntity((UUID) Reflex.invokeMethod(getUniqueId, nmsEntity));
        if (!(bukkitEntity instanceof LivingEntity)) return;
        if (EntityManager.isPacketDuplicatorFixed(bukkitEntity)) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            EntityEquipmentChangeEvent event = new EntityEquipmentChangeEvent((LivingEntity) bukkitEntity);
            plugin.getServer().getPluginManager().callEvent(event);
        });
    }

    protected void manageDamageParticle(@NotNull EnginePlayerPacketEvent e, @NotNull Object packet) {
        Class packetParticlesClass = Reflex.getNMSClass("PacketPlayOutWorldParticles");
        Class particleParamClass   = Reflex.getNMSClass("ParticleParam");

        Object p = packetParticlesClass.cast(packet);

        Object j = Reflex.getFieldValue(p, "j");
        if (j == null) return;

        Method a = Reflex.getMethod(particleParamClass, "a");

        String name = (String) Reflex.invokeMethod(a, j);
        if (name.contains("damage_indicator")) {
            Reflex.setFieldValue(p, "h", 20);
        }
    }

    protected void manageEntityNames(@NotNull EnginePlayerPacketEvent e, @NotNull Object packet) {
        RPGUser user = plugin.getUserManager().getOrLoadUser(e.getReciever());
        if (user == null) return;

        UserProfile         profile   = user.getActiveProfile();
        UserEntityNamesMode namesMode = profile.getNamesMode();
        if (namesMode == UserEntityNamesMode.DEFAULT) return;

        Class pClass = Reflex.getNMSClass("PacketPlayOutEntityMetadata");

        Object p = pClass.cast(packet);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) Reflex.getFieldValue(p, "b");
        if (list == null) return;

        // Hide or show custom entity names
        if (list.size() > 13) {
            Object index3 = list.get(13);

            Method bMethod = Reflex.getMethod(index3.getClass(), "b");

            Object b = Reflex.invokeMethod(bMethod, index3);
            if (b == null || !b.getClass().equals(Boolean.class)) return;
            //Object nameVisible = Reflex.getFieldValue(index3, "b");

            boolean visibility = namesMode == UserEntityNamesMode.ALWAYS_VISIBLE;
            Reflex.setFieldValue(index3, "b", visibility);
        }
    }

    protected void managePlayerHelmet(@NotNull EnginePlayerPacketEvent e, @NotNull Object packet) {
        Class playOutEntityEquipment = Reflex.getNMSClass("PacketPlayOutEntityEquipment");
        Class enumItemSlotClass      = Reflex.getNMSClass("EnumItemSlot");

        Object p = playOutEntityEquipment.cast(packet);

        @SuppressWarnings("unchecked")
        List<Object> slots = (List<Object>) Reflex.getFieldValue(p, "b");
        if (slots == null || !slots.contains(Reflex.getEnum(enumItemSlotClass, "HEAD"))) return;

        Integer entityId = (Integer) Reflex.getFieldValue(p, "a");
        if (entityId == null) return;

        Class craftServerClass = Reflex.getCraftClass("CraftServer");
        Class nmsEntityClass   = Reflex.getNMSClass("Entity");
        Class worldServerClass = Reflex.getNMSClass("WorldServer");

        Object server    = craftServerClass.cast(Bukkit.getServer());
        Object nmsEntity = null;
        Object dedicatedServer = Reflex.invokeMethod(
                Reflex.getMethod(craftServerClass, "getServer"),
                server
        );

        Iterable<?> worlds = (Iterable<?>) Reflex.invokeMethod(
                Reflex.getMethod(dedicatedServer.getClass(), "getWorlds"),
                dedicatedServer
        );

        Method getEntity = Reflex.getMethod(worldServerClass, "getEntity", int.class);
        for (Object worldServer : worlds) {
            nmsEntity = Reflex.invokeMethod(getEntity, worldServer, entityId.intValue());
            if (nmsEntity != null) {
                break;
            }
        }

        if (nmsEntity == null) return;


        Method getUniqueId = Reflex.getMethod(nmsEntityClass, "getUniqueID");

        Entity bukkitEntity = NexEngine.get().getServer().getEntity((UUID) Reflex.invokeMethod(getUniqueId, nmsEntity));
        if (bukkitEntity == null || Hooks.isNPC(bukkitEntity) || !(bukkitEntity instanceof Player)) return;

        Player  player = (Player) bukkitEntity;
        RPGUser user   = plugin.getUserManager().getOrLoadUser(player);
        if (user == null) return;

        UserProfile profile = user.getActiveProfile();
        if (profile.isHideHelmet()) {
            Reflex.setFieldValue(p, "c", Reflex.getFieldValue(Reflex.getNMSClass("ItemStack"), "a"));
        }
    }
}
