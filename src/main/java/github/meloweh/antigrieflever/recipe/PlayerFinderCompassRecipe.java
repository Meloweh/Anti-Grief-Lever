package github.meloweh.antigrieflever.recipe;

import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.item.PlayerFinderCompassItem;
import java.util.regex.Pattern;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class PlayerFinderCompassRecipe extends CustomRecipe {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public PlayerFinderCompassRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return targetName(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        String targetName = targetName(input);
        return targetName == null ? ItemStack.EMPTY : PlayerFinderCompassItem.create(targetName);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Antigrieflever.PLAYER_FINDER_COMPASS_RECIPE.get();
    }

    private static String targetName(CraftingInput input) {
        if (input.width() != 3 || input.height() != 3 || input.ingredientCount() != 5) {
            return null;
        }

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (slot == 1 || slot == 3 || slot == 5 || slot == 7) {
                if (!stack.is(Items.COPPER_INGOT)) {
                    return null;
                }
            } else if (slot == 4) {
                if (!stack.is(Items.NAME_TAG) || !stack.has(DataComponents.CUSTOM_NAME)) {
                    return null;
                }
            } else if (!stack.isEmpty()) {
                return null;
            }
        }

        String name = input.getItem(4).get(DataComponents.CUSTOM_NAME).getString().trim();
        return USERNAME.matcher(name).matches() ? name : null;
    }
}
