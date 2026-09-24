package com.parkourmod;

/** Интерфейс, который через миксин добавляется в AvatarRenderState: переносит позу анимаций в рендер. */
public interface PoseHolder {
    EmoteAnimator.PoseSet parkourmod$getPoseSet();

    void parkourmod$setPoseSet(EmoteAnimator.PoseSet poses);
}
