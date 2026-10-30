package com.ruskserver.iem_compatibility.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menuがあるときだけ呼ばれる任意の入口。 */
public final class IemModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() { return IemConfigScreen::create; }
}
