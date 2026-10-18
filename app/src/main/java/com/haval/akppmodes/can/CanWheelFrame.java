package com.haval.akppmodes.can;

/**
 * Кадр CAN id 0x244 (скан кодов руля), 8 байт.
 *
 * byte0  — CRC
 * byte1  — up/down/left/right (битовая маска)
 * byte2  — cruise+/−, voice
 * byte3  — ok/play
 * byte4  — star (*)
 * byte5  — menu
 * byte6  — mute (bit5), back (bit1)  [нумерация бит 1=LSB]
 * byte7  — lim, cruise dist; младшие 4 бита — счётчик
 *
 * Нумерация бит в описании: «бит 1» = LSB (value & 0x01).
 */
public final class CanWheelFrame {

    public static final int CAN_ID = 0x244;

    public final int counter;
    public final boolean up;
    public final boolean down;
    public final boolean left;
    public final boolean right;
    public final boolean cruisePlus;
    public final boolean cruiseMinus;
    public final boolean voice;
    public final boolean okPlay;
    public final boolean star;
    public final boolean menu;
    public final boolean mute;
    public final boolean back;
    public final boolean lim;
    public final boolean cruiseDist;
    public final int crc;

    private CanWheelFrame(int crc, int counter,
                          boolean up, boolean down, boolean left, boolean right,
                          boolean cruisePlus, boolean cruiseMinus, boolean voice,
                          boolean okPlay, boolean star, boolean menu,
                          boolean mute, boolean back, boolean lim, boolean cruiseDist) {
        this.crc = crc;
        this.counter = counter;
        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
        this.cruisePlus = cruisePlus;
        this.cruiseMinus = cruiseMinus;
        this.voice = voice;
        this.okPlay = okPlay;
        this.star = star;
        this.menu = menu;
        this.mute = mute;
        this.back = back;
        this.lim = lim;
        this.cruiseDist = cruiseDist;
    }

    /** bitIndex 1..8 → LSB = 1 */
    private static boolean bit(int b, int bitIndex1to8) {
        return ((b >> (bitIndex1to8 - 1)) & 1) == 1;
    }

    public static CanWheelFrame parse(byte[] data) {
        if (data == null || data.length < 8) return null;
        int b0 = data[0] & 0xFF;
        int b1 = data[1] & 0xFF;
        int b2 = data[2] & 0xFF;
        int b3 = data[3] & 0xFF;
        int b4 = data[4] & 0xFF;
        int b5 = data[5] & 0xFF;
        int b6 = data[6] & 0xFF;
        int b7 = data[7] & 0xFF;

        return new CanWheelFrame(
                b0,
                b7 & 0x0F,
                bit(b1, 7), bit(b1, 5), bit(b1, 3), bit(b1, 1),
                bit(b2, 7), bit(b2, 5), bit(b2, 3),
                bit(b3, 7),
                bit(b4, 7),
                bit(b5, 2),
                bit(b6, 5), bit(b6, 1),
                bit(b7, 7), bit(b7, 5)
        );
    }

    @Override
    public String toString() {
        return "CanWheel{ctr=" + counter
                + " mute=" + mute + " back=" + back
                + " up=" + up + " down=" + down
                + " left=" + left + " right=" + right
                + " ok=" + okPlay + " menu=" + menu
                + " star=" + star + "}";
    }
}
