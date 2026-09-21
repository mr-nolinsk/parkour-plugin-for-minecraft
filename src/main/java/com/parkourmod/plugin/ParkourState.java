package com.parkourmod.plugin;

import org.bukkit.Location;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.util.Vector;

/** Рантайм-состояние паркур-режима одного игрока. Живёт, пока игрок онлайн. */
public final class ParkourState {

    public enum State { NONE, CLING, WALL_RUN, MANTLE, DASH_CHARGE, SLIDE }

    public boolean enabled = false;
    public State state = State.NONE;

    public Vector wallDir = new Vector();
    public Vector runDir = new Vector();
    public double runSpeed;
    public int stateTicks;

    public int airTicks;
    public int noGrabTicks;
    public int wallJumpTicks;

    // --- рывок (dash) ---
    public float dashPhase;
    public int dashCooldown;

    // --- скольжение (slide) ---
    public Vector slideDir = new Vector();
    public double slideSpeed;
    public double slideDecay;
    public double slideTraveled;
    public int slideAirTicks;
    public int slideCooldown;

    // --- разбег (burst) ---
    public int burstTicks;
    public int burstElapsed;
    public int burstCooldown;
    public AttributeModifier burstModifier;

    // --- общее движение ---
    public boolean wasOnGround = true;
    public double recentSpeed;
    public double lastDisp;
    public Vector lastMoveDir = new Vector();
    public Location lastLocation;
}
