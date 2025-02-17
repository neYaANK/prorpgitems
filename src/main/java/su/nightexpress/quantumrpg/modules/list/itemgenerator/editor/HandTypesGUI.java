package su.nightexpress.quantumrpg.modules.list.itemgenerator.editor;

import mc.promcteam.engine.manager.api.menu.Slot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import su.nightexpress.quantumrpg.stats.items.ItemStats;
import su.nightexpress.quantumrpg.stats.items.attributes.HandAttribute;

public class HandTypesGUI extends AbstractEditorGUI {

    public HandTypesGUI(Player player, ItemGeneratorReference itemGenerator) {
        super(player, 6, "[&d" + itemGenerator.getId() + "&r] editor/" + EditorGUI.ItemType.HAND_TYPES.getTitle(), itemGenerator);
    }

    @Override
    public void setContents() {
        int i = 0;
        for (HandAttribute hand : ItemStats.getHands()) {
            i++;
            if (i % this.inventory.getSize() == 53) {
                this.setSlot(i, getNextButton());
                i++;
            } else if (i % 9 == 8) {i++;}
            if (i % this.inventory.getSize() == 45) {
                this.setSlot(i, getPrevButton());
                i++;
            } else if (i % 9 == 0) {i++;}

            String id = hand.getId().toUpperCase();
            this.setSlot(i, new Slot(createItem(Material.STICK,
                    "&e" + hand.getName(),
                    "&bCurrent: &a" + itemGenerator.getConfig().getDouble(EditorGUI.ItemType.HAND_TYPES.getPath() + '.' + id, 0),
                    "&6Left-Click: &eSet",
                    "&6Drop: &eRemove")) {
                @Override
                public void onLeftClick() {
                    sendSetMessage(id,
                            String.valueOf(itemGenerator.getConfig().getDouble(EditorGUI.ItemType.HAND_TYPES.getPath() + '.' + id, 0)),
                            s -> {
                                double chance = Double.parseDouble(s);
                                if (chance == 0) {
                                    itemGenerator.getConfig().remove(EditorGUI.ItemType.HAND_TYPES.getPath() + '.' + id);
                                } else if (chance > 0) {
                                    itemGenerator.getConfig().set(EditorGUI.ItemType.HAND_TYPES.getPath() + '.' + id, chance);
                                } else {
                                    throw new IllegalArgumentException();
                                }
                                saveAndReopen();
                            });
                }

                @Override
                public void onDrop() {
                    itemGenerator.getConfig().remove(EditorGUI.ItemType.HAND_TYPES.getPath() + '.' + id);
                    saveAndReopen();
                }
            });
        }
        this.setSlot(this.getPages() * this.inventory.getSize() - 9, getPrevButton());
        this.setSlot(this.getPages() * this.inventory.getSize() - 1, getNextButton());
    }
}
