package com.parkourmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Все клавиши мода (Настройки → Управление → Parkour Mod).
 * <p>
 * Клавиши действий (рывок, скольжение, фиксация, прыжок от стены, залезание) по умолчанию НЕ назначены.
 * Пока клавиша не назначена, действие работает на стандартной клавише (Шифт / Прыжок / W).
 * Как только вы назначили свою клавишу - действие работает только на ней.
 */
public final class ParkourKeys {
    private ParkourKeys() {}

    /** Включить/выключить паркур-режим. */
    public static KeyMapping toggle;
    /** Разбег (спринт-рывок). */
    public static KeyMapping burst;
    /** Заряд рывка (пусто = Прыжок + Шифт). */
    public static KeyMapping dash;
    /** Скольжение по полу (пусто = Шифт на бегу). */
    public static KeyMapping slide;
    /** Фиксация на стене, удерживать (пусто = Шифт). */
    public static KeyMapping cling;
    /** Прыжок от стены (пусто = Прыжок). */
    public static KeyMapping wallJump;
    /** Залезть на уступ при фиксации (пусто = W). */
    public static KeyMapping climb;

    public static void register(KeyMapping.Category category) {
        toggle = make("toggle", InputConstants.KEY_G, category);
        burst = make("burst", InputConstants.KEY_R, category);
        dash = make("dash", -1, category);
        slide = make("slide", -1, category);
        cling = make("cling", -1, category);
        wallJump = make("wall_jump", -1, category);
        climb = make("climb", -1, category);
    }

    private static KeyMapping make(String id, int keyCode, KeyMapping.Category category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.parkourmod." + id, InputConstants.Type.KEYSYM, keyCode, category));
    }

    /** Назначена ли у клавиши действия своя кнопка. */
    public static boolean isBound(KeyMapping key) {
        return key != null && !key.isUnbound();
    }

    /** Зажата ли своя клавиша; если она не назначена - используем стандартную (fallback). */
    public static boolean down(KeyMapping custom, boolean fallback) {
        return isBound(custom) ? custom.isDown() : fallback;
    }

    /** Сбрасывает накопленные "клики" клавиш, которые мы читаем только через isDown(). */
    public static void drainHeldKeys() {
        KeyMapping[] keys = {dash, slide, cling, wallJump, climb};
        for (KeyMapping key : keys) {
            while (key.consumeClick()) { /* нужен только isDown() */ }
        }
    }
}
