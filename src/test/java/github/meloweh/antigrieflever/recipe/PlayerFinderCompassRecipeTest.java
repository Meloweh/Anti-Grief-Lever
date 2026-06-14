package github.meloweh.antigrieflever.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.item.PlayerFinderCompassItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import org.junit.jupiter.api.Test;

class PlayerFinderCompassRecipeTest {
    private final PlayerFinderCompassRecipe recipe =
        new PlayerFinderCompassRecipe(CraftingBookCategory.EQUIPMENT);

    @Test
    void createsFinderFromNamedNameTag() {
        CraftingInput input = compassInput(namedTag("Player1"));

        assertTrue(recipe.matches(input, null));
        ItemStack result = recipe.assemble(input, null);
        assertTrue(result.is(Antigrieflever.PLAYER_FINDER_COMPASS.get()));
        assertEquals("Player1 finder", result.getHoverName().getString());
        assertEquals("Player1", PlayerFinderCompassItem.targetName(result).orElseThrow());
    }

    @Test
    void rejectsUnnamedOrInvalidPlayerNames() {
        assertFalse(recipe.matches(compassInput(new ItemStack(Items.NAME_TAG)), null));
        assertFalse(recipe.matches(compassInput(namedTag("invalid player")), null));
        assertFalse(recipe.matches(compassInput(namedTag("seventeen_chars__")), null));
    }

    @Test
    void rejectsIncorrectCompassShape() {
        List<ItemStack> stacks = emptyGrid();
        stacks.set(4, namedTag("Player1"));
        stacks.set(1, new ItemStack(Items.COPPER_INGOT));
        stacks.set(3, new ItemStack(Items.COPPER_INGOT));
        stacks.set(5, new ItemStack(Items.COPPER_INGOT));

        assertFalse(recipe.matches(CraftingInput.of(3, 3, stacks), null));
    }

    @Test
    void rejectsIronIngots() {
        List<ItemStack> stacks = emptyGrid();
        stacks.set(1, new ItemStack(Items.IRON_INGOT));
        stacks.set(3, new ItemStack(Items.IRON_INGOT));
        stacks.set(4, namedTag("Player1"));
        stacks.set(5, new ItemStack(Items.IRON_INGOT));
        stacks.set(7, new ItemStack(Items.IRON_INGOT));

        assertFalse(recipe.matches(CraftingInput.of(3, 3, stacks), null));
    }

    private static CraftingInput compassInput(ItemStack nameTag) {
        List<ItemStack> stacks = emptyGrid();
        stacks.set(1, new ItemStack(Items.COPPER_INGOT));
        stacks.set(3, new ItemStack(Items.COPPER_INGOT));
        stacks.set(4, nameTag);
        stacks.set(5, new ItemStack(Items.COPPER_INGOT));
        stacks.set(7, new ItemStack(Items.COPPER_INGOT));
        return CraftingInput.of(3, 3, stacks);
    }

    private static ItemStack namedTag(String name) {
        ItemStack stack = new ItemStack(Items.NAME_TAG);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static List<ItemStack> emptyGrid() {
        return new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
    }
}
