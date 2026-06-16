package github.meloweh.antigrieflever.client;

import github.meloweh.antigrieflever.network.ModNetwork.SaveWarpStonePayload;
import github.meloweh.antigrieflever.network.ModNetwork.WarpStoneOption;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WarpStoneConfigScreen extends Screen {
    private static final int OPTIONS_PER_PAGE = 6;

    private final BlockPos stonePos;
    private final List<WarpStoneOption> options;
    private String nameDraft;
    private int page;
    private EditBox nameInput;

    public WarpStoneConfigScreen(BlockPos stonePos, String initialName, List<WarpStoneOption> options) {
        super(Component.translatable("screen.antigrieflever.warp_stone.title"));
        this.stonePos = stonePos;
        this.nameDraft = initialName;
        this.options = List.copyOf(options);
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = Math.max(30, height / 2 - 100);
        nameInput = new EditBox(font, centerX - 150, top + 26, 300, 20, Component.translatable("screen.antigrieflever.warp_stone.name"));
        nameInput.setMaxLength(64);
        nameInput.setValue(nameDraft);
        nameInput.setResponder(value -> nameDraft = value);
        addRenderableWidget(nameInput);

        addRenderableWidget(
            Button.builder(Component.translatable("screen.antigrieflever.warp_stone.save_name"), ignored -> saveName())
                .bounds(centerX - 150, top + 52, 300, 20)
                .build()
        );

        int first = page * OPTIONS_PER_PAGE;
        int last = Math.min(options.size(), first + OPTIONS_PER_PAGE);
        for (int index = first; index < last; index++) {
            WarpStoneOption option = options.get(index);
            int row = index - first;
            addRenderableWidget(
                Button.builder(optionLabel(option), ignored -> link(option))
                    .bounds(centerX - 150, top + 84 + row * 24, 300, 20)
                    .build()
            );
        }

        int pages = Math.max(1, (options.size() + OPTIONS_PER_PAGE - 1) / OPTIONS_PER_PAGE);
        Button previous = addRenderableWidget(
            Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(centerX - 150, top + 84 + OPTIONS_PER_PAGE * 24, 44, 20)
                .build()
        );
        Button next = addRenderableWidget(
            Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(centerX + 106, top + 84 + OPTIONS_PER_PAGE * 24, 44, 20)
                .build()
        );
        previous.active = page > 0;
        next.active = page + 1 < pages;
        setInitialFocus(nameInput);
    }

    private Component optionLabel(WarpStoneOption option) {
        BlockPos pos = option.pos();
        String label = option.name() + " [" + option.dimension() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "]";
        if (label.length() > 54) {
            label = label.substring(0, 51) + "...";
        }
        return Component.literal(label);
    }

    private void changePage(int delta) {
        nameDraft = nameInput.getValue();
        int pages = Math.max(1, (options.size() + OPTIONS_PER_PAGE - 1) / OPTIONS_PER_PAGE);
        page = Math.max(0, Math.min(pages - 1, page + delta));
        rebuildWidgets();
    }

    private void saveName() {
        PacketDistributor.sendToServer(new SaveWarpStonePayload(stonePos, nameInput.getValue(), "", BlockPos.ZERO, false));
        onClose();
    }

    private void link(WarpStoneOption option) {
        PacketDistributor.sendToServer(new SaveWarpStonePayload(stonePos, nameInput.getValue(), option.dimension(), option.pos(), true));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            saveName();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int top = Math.max(30, height / 2 - 100);
        graphics.drawCenteredString(font, title, centerX, top, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.antigrieflever.warp_stone.name"), centerX - 150, top + 15, 0xA0A0A0);
        graphics.drawCenteredString(font, Component.translatable("screen.antigrieflever.warp_stone.available"), centerX, top + 75, 0xA0A0A0);
        if (options.isEmpty()) {
            graphics.drawCenteredString(
                font,
                Component.translatable("screen.antigrieflever.warp_stone.none"),
                centerX,
                top + 92,
                0xFFAA55
            );
        }
    }
}
