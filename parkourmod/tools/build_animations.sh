#!/bin/sh
# Пересобирает AnimClips.java из файлов tools/emotes/*.emotecraft
cd "$(dirname "$0")"
python3 emotecraft_to_java.py -o ../src/main/java/com/parkourmod/AnimClips.java \
  RUN=emotes/run.emotecraft:4-28 \
  WALL_RUN_LEFT=emotes/wall_run_left.emotecraft:4-16 \
  WALL_RUN_RIGHT=emotes/wall_run_right.emotecraft:4-16 \
  WALL_JUMP=emotes/wall_jump.emotecraft
