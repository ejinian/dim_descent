package com.ejinian.dimdescent.client;

import com.ejinian.dimdescent.item.AlmanacusItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;

// Client-only entry point for opening the Almanacus. Kept in its own class so that a dedicated
// server never loads BookViewScreen: AlmanacusItem only reaches this from inside an
// isClientSide branch, which on a server never executes, so the class is never resolved.
public final class AlmanacusReader {

    public static void open() {
        // BookAccess is just a record of pages, so the vanilla reader can be handed arbitrary
        // content - no need to fabricate a written-book ItemStack to render our own pages.
        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess(AlmanacusItem.pages())));
    }

    private AlmanacusReader() {
    }
}
