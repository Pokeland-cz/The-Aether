package com.aetherteam.aether.client.gui.screen.menu;

import net.minecraft.client.gui.Font;

public interface CustomBranding {
    int getBrandingTextX(Font font, String text, int x);

    int getBrandingTextY(Font font, String text, int y);
}
