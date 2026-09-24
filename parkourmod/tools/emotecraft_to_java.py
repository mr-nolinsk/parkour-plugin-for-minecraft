#!/usr/bin/env python3
"""
Конвертер бинарных файлов Emotecraft (.emotecraft) в Java-класс AnimClips.

Формат разобран по логике загрузчика PlayerAnimator (LegacyAnimationBinary, версии 1 и 2).
Все каналы "запекаются" с учётом easing в равномерные отсчёты (RATE отсчётов на тик), поэтому
в самом моде достаточно линейной интерполяции.

Использование:
    python3 emotecraft_to_java.py -o ../src/main/java/com/parkourmod/AnimClips.java \
        RUN=emotes/run.emotecraft:4-28 WALL_JUMP=emotes/wall_jump.emotecraft ...
Формат аргумента:  ИМЯ=файл[:начало-конец]   где начало-конец - тики зацикленного участка.
Без участка клип проигрывается один раз (последний кадр удерживается).
Если участок не указан, но в файле включён loop, берётся returnTick-1 .. endTick.
"""
import math
import struct
import sys

HEADER_SIZE = 16
RATE = 2  # отсчётов на тик
DEFAULTS = {
    'rightArm': (-5.0, 2.0, 0.0), 'leftArm': (5.0, 2.0, 0.0),
    'leftLeg': (1.9, 12.0, 0.1), 'rightLeg': (-1.9, 12.0, 0.1),
    'head': (0.0, 0.0, 0.0), 'body': (0.0, 0.0, 0.0),
}
PART_ORDER = ['head', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg']
V1_PARTS = ('head', 'body', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg')


def parse(data):
    version = struct.unpack('>H', data[6:8])[0]
    p = HEADER_SIZE

    def i32():
        nonlocal p
        v = struct.unpack('>i', data[p:p + 4])[0]; p += 4; return v

    def f32():
        nonlocal p
        v = struct.unpack('>f', data[p:p + 4])[0]; p += 4; return v

    def u8():
        nonlocal p
        v = data[p]; p += 1; return v

    def s8():
        nonlocal p
        v = struct.unpack('>b', data[p:p + 1])[0]; p += 1; return v

    def string():
        nonlocal p
        n = i32(); s = data[p:p + n].decode('utf-8'); p += n; return s

    anim = {'version': version, 'begin': i32(), 'end': i32(), 'stop': i32(), 'loop': u8() != 0,
            'return': i32(), 'easeBefore': u8() != 0}
    if anim['easeBefore']:
        raise SystemExit('easeBefore=true не поддерживается')
    u8()  # nsfw
    ks = s8()

    def read_part(name):
        nonlocal p
        is_item = 'tem' in name
        chans = ['posX', 'posY', 'posZ', 'rotX', 'rotY', 'rotZ']
        if name != 'head' and not is_item:
            chans += ['bendY', 'bend']
        if version >= 3:
            chans += ['scaleX', 'scaleY', 'scaleZ']
        part = {}
        for ch in chans:
            if version >= 2:
                enabled = u8() != 0
                length = i32()
            else:
                length = i32()
                enabled = length >= 0
            frames = None
            if not enabled:
                p += max(length, 0) * ks
            else:
                frames = []
                for _ in range(length):
                    q = p
                    tick = i32(); val = f32(); ease = s8()
                    p = q + ks
                    frames.append((tick, val, ease))
            part[ch] = frames
        return part

    parts = {}
    if version >= 2:
        for _ in range(i32()):
            name = string()
            parts[name] = read_part(name)
    else:
        for name in V1_PARTS:
            parts[name] = read_part(name)
    anim['parts'] = parts
    return anim


# ---------------------------------------------------------------- easing (id -> функция 0..1)
def _in_out(f_in):
    def f(x):
        return f_in(2 * x) / 2 if x < 0.5 else 1 - f_in(2 * (1 - x)) / 2
    return f


def _family(power):
    fin = lambda x: x ** power
    fout = lambda x: 1 - (1 - x) ** power
    return fin, fout, _in_out(fin)


def _build_easings():
    e = {0: lambda x: x, 1: lambda x: 0.0}
    s_in = lambda x: 1 - math.cos(x * math.pi / 2)
    s_out = lambda x: math.sin(x * math.pi / 2)
    e[6], e[7], e[8] = s_in, s_out, lambda x: -(math.cos(math.pi * x) - 1) / 2
    for base, power in ((9, 3), (12, 2), (15, 4), (18, 5)):
        e[base], e[base + 1], e[base + 2] = _family(power)
    x_in = lambda x: 0.0 if x == 0 else 2 ** (10 * x - 10)
    x_out = lambda x: 1.0 if x == 1 else 1 - 2 ** (-10 * x)
    e[21], e[22], e[23] = x_in, x_out, _in_out(x_in)
    c_in = lambda x: 1 - math.sqrt(max(0.0, 1 - x * x))
    c_out = lambda x: math.sqrt(max(0.0, 1 - (x - 1) ** 2))
    e[24], e[25], e[26] = c_in, c_out, _in_out(c_in)
    return e


EASINGS = _build_easings()


def bake(frames, convert, length):
    """Значения канала в моменты t = i / RATE, i = 0..length*RATE (easeBefore=false)."""
    frames = sorted(frames)
    vals = [(t, convert(v), e) for t, v, e in frames]
    out = []
    for i in range(length * RATE + 1):
        t = i / RATE
        if t >= vals[-1][0]:
            out.append(vals[-1][1]); continue
        if t <= vals[0][0]:
            t0, v0, ease = 0.0, 0.0, 8            # вход из нейтральной позы: easeInOutSine
            t1, v1 = vals[0][0], vals[0][1]
        else:
            k = max(j for j, (tt, _, _) in enumerate(vals) if tt <= t)
            t0, v0, ease = vals[k]
            t1, v1 = vals[k + 1][0], vals[k + 1][1]
        if ease not in EASINGS:
            raise SystemExit('easing id %d не поддерживается' % ease)
        x = (t - t0) / (t1 - t0) if t1 > t0 else 1.0
        out.append(v0 + (v1 - v0) * EASINGS[ease](x))
    return out


def clip_to_data(anim, loop):
    parts = anim['parts']
    length = anim['end']
    stop = anim['stop']
    if loop is None and anim['loop']:
        loop = (max(anim['return'] - 1, 0), length)
    chans = []
    for name in PART_ORDER:
        d = DEFAULTS[name]
        spec = (('posX', lambda v, d=d: v - d[0]), ('posY', lambda v, d=d: v - d[1]),
                ('posZ', lambda v, d=d: v - d[2]), ('rotX', lambda v: v),
                ('rotY', lambda v: v), ('rotZ', lambda v: v))
        for ch, conv in spec:
            fr = parts[name][ch]
            if not fr:
                raise SystemExit('канал %s.%s отключён - не поддерживается' % (name, ch))
            chans.append(bake(fr, conv, length))
    for ch in ('posX', 'posY', 'posZ', 'rotX', 'rotY', 'rotZ'):
        fr = parts['body'][ch]
        if not fr:
            raise SystemExit('канал body.%s отключён - не поддерживается' % ch)
        chans.append(bake(fr, lambda v: v, length))
    return length, loop, chans


def main():
    args = sys.argv[1:]
    if len(args) < 3 or args[0] != '-o':
        raise SystemExit(__doc__)
    dst = args[1]
    clips = []
    for spec in args[2:]:
        name, rest = spec.split('=', 1)
        loop = None
        if ':' in rest:
            rest, loop_s = rest.rsplit(':', 1)
            a, b = loop_s.split('-')
            loop = (int(a), int(b))
        anim = parse(open(rest, 'rb').read())
        length, loop, chans = clip_to_data(anim, loop)
        clips.append((name, length, loop, chans))
        print('%s: %d тиков, цикл %s' % (name, length, loop))

    out = ['package com.parkourmod;', '',
           '/** Сгенерировано tools/emotecraft_to_java.py. Не редактировать вручную. */',
           'public final class AnimClips {', '    private AnimClips() {}', '']
    for name, length, loop, chans in clips:
        def fmt(v):
            t = ('%.5f' % v).rstrip('0').rstrip('.')
            return '0' if t in ('', '-0') else t
        data = ' '.join(fmt(v) for ch in chans for v in ch)
        lf, lt = loop if loop else (-1, -1)
        out.append('    public static final AnimClip %s = AnimClip.parse("%s", %d, %d, %d, %d,' % (name, name, length, lf, lt, RATE))
        out.append('            "%s");' % data)
        out.append('')
    out.append('}')
    open(dst, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
    print('OK ->', dst)


if __name__ == '__main__':
    main()
