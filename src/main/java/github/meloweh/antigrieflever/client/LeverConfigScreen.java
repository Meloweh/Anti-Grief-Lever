package github.meloweh.antigrieflever.client;

import github.meloweh.antigrieflever.network.ModNetwork.SaveConfigPayload;
import github.meloweh.antigrieflever.protection.ProtectionRegion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class LeverConfigScreen extends Screen {
    private final BlockPos leverPos;
    private final String initialDefinition;
    private final int maxRadius;
    private EditBox regionInput;
    private Button saveButton;
    private String validationError = "";

    public LeverConfigScreen(BlockPos leverPos, String initialDefinition, int maxRadius) {
        super(Component.translatable("screen.antigrieflever.title"));
        this.leverPos = leverPos;
        this.initialDefinition = initialDefinition;
        this.maxRadius = maxRadius;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        regionInput = new EditBox(font, centerX - 150, height / 2 - 20, 300, 20, Component.translatable("screen.antigrieflever.region"));
        regionInput.setMaxLength(96);
        regionInput.setHint(Component.literal("[radius], [width,height], or [x1,y1,z1,x2,y2,z2]"));
        regionInput.setValue(initialDefinition);
        regionInput.setResponder(ignored -> validate());
        addRenderableWidget(regionInput);

        saveButton = addRenderableWidget(
            Button.builder(Component.translatable("gui.done"), ignored -> save())
                .bounds(centerX - 75, height / 2 + 18, 150, 20)
                .build()
        );
        validate();
        setInitialFocus(regionInput);
    }

    private void validate() {
        if (regionInput == null || saveButton == null) {
            return;
        }
        ProtectionRegion.ParseResult parsed =
            ProtectionRegion.parse(regionInput.getValue(), leverPos, maxRadius);
        saveButton.active = parsed.valid();
        validationError = parsed.valid() ? "" : parsed.error();
    }

    private void save() {
        ProtectionRegion.ParseResult parsed =
            ProtectionRegion.parse(regionInput.getValue(), leverPos, maxRadius);
        if (!parsed.valid()) {
            validationError = parsed.error();
            return;
        }
        PacketDistributor.sendToServer(new SaveConfigPayload(leverPos, parsed.canonicalDefinition()));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && saveButton.active) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, height / 2 - 72, 0xFFFFFF);
        graphics.drawCenteredString(
            font,
            Component.translatable("screen.antigrieflever.help", maxRadius),
            centerX,
            height / 2 - 55,
            0xA0A0A0
        );
        if (!validationError.isEmpty()) {
            graphics.drawCenteredString(font, validationError, centerX, height / 2 + 45, 0xFF5555);
        }
    }
}
