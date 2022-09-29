/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.keventreader;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class KeypressEvent implements Parcelable {
    private static final Map<Integer, String> KEYCODE_NAME_MAP = new HashMap<Integer, String>() {{
        put(0,"RESERVED");
        put(1,"ESC");
        put(2,"1");
        put(3,"2");
        put(4,"3");
        put(5,"4");
        put(6,"5");
        put(7,"6");
        put(8,"7");
        put(9,"8");
        put(10,"9");
        put(11,"0");
        put(12,"MINUS");
        put(13,"EQUAL");
        put(14,"BACKSPACE");
        put(15,"TAB");
        put(16,"Q");
        put(17,"W");
        put(18,"E");
        put(19,"R");
        put(20,"T");
        put(21,"Y");
        put(22,"U");
        put(23,"I");
        put(24,"O");
        put(25,"P");
        put(26,"LEFTBRACE");
        put(27,"RIGHTBRACE");
        put(28,"ENTER");
        put(29,"LEFTCTRL");
        put(30,"A");
        put(31,"S");
        put(32,"D");
        put(33,"F");
        put(34,"G");
        put(35,"H");
        put(36,"J");
        put(37,"K");
        put(38,"L");
        put(39,"SEMICOLON");
        put(40,"APOSTROPHE");
        put(41,"GRAVE");
        put(42,"LEFTSHIFT");
        put(43,"BACKSLASH");
        put(44,"Z");
        put(45,"X");
        put(46,"C");
        put(47,"V");
        put(48,"B");
        put(49,"N");
        put(50,"M");
        put(51,"COMMA");
        put(52,"DOT");
        put(53,"SLASH");
        put(54,"RIGHTSHIFT");
        put(55,"KPASTERISK");
        put(56,"LEFTALT");
        put(57,"SPACE");
        put(58,"CAPSLOCK");
        put(59,"F1");
        put(60,"F2");
        put(61,"F3");
        put(62,"F4");
        put(63,"F5");
        put(64,"F6");
        put(65,"F7");
        put(66,"F8");
        put(67,"F9");
        put(68,"F10");
        put(69,"NUMLOCK");
        put(70,"SCROLLLOCK");
        put(71,"KP7");
        put(72,"KP8");
        put(73,"KP9");
        put(74,"KPMINUS");
        put(75,"KP4");
        put(76,"KP5");
        put(77,"KP6");
        put(78,"KPPLUS");
        put(79,"KP1");
        put(80,"KP2");
        put(81,"KP3");
        put(82,"KP0");
        put(83,"KPDOT");
        put(85,"ZENKAKUHANKAKU");
        put(86,"102ND");
        put(87,"F11");
        put(88,"F12");
        put(89,"RO");
        put(90,"KATAKANA");
        put(91,"HIRAGANA");
        put(92,"HENKAN");
        put(93,"KATAKANAHIRAGANA");
        put(94,"MUHENKAN");
        put(95,"KPJPCOMMA");
        put(96,"KPENTER");
        put(97,"RIGHTCTRL");
        put(98,"KPSLASH");
        put(99,"SYSRQ");
        put(100,"RIGHTALT");
        put(101,"LINEFEED");
        put(102,"HOME");
        put(103,"UP");
        put(104,"PAGEUP");
        put(105,"LEFT");
        put(106,"RIGHT");
        put(107,"END");
        put(108,"DOWN");
        put(109,"PAGEDOWN");
        put(110,"INSERT");
        put(111,"DELETE");
        put(112,"MACRO");
        put(113,"MUTE");
        put(114,"VOLUMEDOWN");
        put(115,"VOLUMEUP");
        put(116,"POWER");
        put(117,"KPEQUAL");
        put(118,"KPPLUSMINUS");
        put(119,"PAUSE");
        put(120,"SCALE");
        put(121,"KPCOMMA");
        put(122,"HANGEUL");
        put(123,"HANJA");
        put(124,"YEN");
        put(125,"LEFTMETA");
        put(126,"RIGHTMETA");
        put(127,"COMPOSE");
        put(128,"STOP");
        put(129,"AGAIN");
        put(130,"PROPS");
        put(131,"UNDO");
        put(132,"FRONT");
        put(133,"COPY");
        put(134,"OPEN");
        put(135,"PASTE");
        put(136,"FIND");
        put(137,"CUT");
        put(138,"HELP");
        put(139,"MENU");
        put(140,"CALC");
        put(141,"SETUP");
        put(142,"SLEEP");
        put(143,"WAKEUP");
        put(144,"FILE");
        put(145,"SENDFILE");
        put(146,"DELETEFILE");
        put(147,"XFER");
        put(148,"PROG1");
        put(149,"PROG2");
        put(150,"WWW");
        put(151,"MSDOS");
        put(152,"SCREENLOCK");
        put(153,"ROTATE_DISPLAY");
        put(154,"CYCLEWINDOWS");
        put(155,"MAIL");
        put(156,"BOOKMARKS");
        put(157,"COMPUTER");
        put(158,"BACK");
        put(159,"FORWARD");
        put(160,"CLOSECD");
        put(161,"EJECTCD");
        put(162,"EJECTCLOSECD");
        put(163,"NEXTSONG");
        put(164,"PLAYPAUSE");
        put(165,"PREVIOUSSONG");
        put(166,"STOPCD");
        put(167,"RECORD");
        put(168,"REWIND");
        put(169,"PHONE");
        put(170,"ISO");
        put(171,"CONFIG");
        put(172,"HOMEPAGE");
        put(173,"REFRESH");
        put(174,"EXIT");
        put(175,"MOVE");
        put(176,"EDIT");
        put(177,"SCROLLUP");
        put(178,"SCROLLDOWN");
        put(179,"KPLEFTPAREN");
        put(180,"KPRIGHTPAREN");
        put(181,"NEW");
        put(182,"REDO");
        put(183,"F13");
        put(184,"F14");
        put(185,"F15");
        put(186,"F16");
        put(187,"F17");
        put(188,"F18");
        put(189,"F19");
        put(190,"F20");
        put(191,"F21");
        put(192,"F22");
        put(193,"F23");
        put(194,"F24");
        put(200,"PLAYCD");
        put(201,"PAUSECD");
        put(202,"PROG3");
        put(203,"PROG4");
        put(204,"DASHBOARD");
        put(205,"SUSPEND");
        put(206,"CLOSE");
        put(207,"PLAY");
        put(208,"FASTFORWARD");
        put(209,"BASSBOOST");
        put(210,"PRINT");
        put(211,"HP");
        put(212,"CAMERA");
        put(213,"SOUND");
        put(214,"QUESTION");
        put(215,"EMAIL");
        put(216,"CHAT");
        put(217,"SEARCH");
        put(218,"CONNECT");
        put(219,"FINANCE");
        put(220,"SPORT");
        put(221,"SHOP");
        put(222,"ALTERASE");
        put(223,"CANCEL");
        put(224,"BRIGHTNESSDOWN");
        put(225,"BRIGHTNESSUP");
        put(226,"MEDIA");
        put(227,"SWITCHVIDEOMODE");
        put(228,"KBDILLUMTOGGLE");
        put(229,"KBDILLUMDOWN");
        put(230,"KBDILLUMUP");
        put(231,"SEND");
        put(232,"REPLY");
        put(233,"FORWARDMAIL");
        put(234,"SAVE");
        put(235,"DOCUMENTS");
        put(236,"BATTERY");
        put(237,"BLUETOOTH");
        put(238,"WLAN");
        put(239,"UWB");
        put(240,"UNKNOWN");
        put(241,"VIDEO_NEXT");
        put(242,"VIDEO_PREV");
        put(243,"BRIGHTNESS_CYCLE");
        put(244,"BRIGHTNESS_AUTO");
        put(245,"DISPLAY_OFF");
        put(246,"WWAN");
        put(247,"RFKILL");
        put(248,"MICMUTE");
        put(0x160,"OK");
        put(0x161,"SELECT");
        put(0x162,"GOTO");
        put(0x163,"CLEAR");
        put(0x164,"POWER2");
        put(0x165,"OPTION");
        put(0x166,"INFO");
        put(0x167,"TIME");
        put(0x168,"VENDOR");
        put(0x169,"ARCHIVE");
        put(0x16a,"PROGRAM");
        put(0x16b,"CHANNEL");
        put(0x16c,"FAVORITES");
        put(0x16d,"EPG");
        put(0x16e,"PVR");
        put(0x16f,"MHP");
        put(0x170,"LANGUAGE");
        put(0x171,"TITLE");
        put(0x172,"SUBTITLE");
        put(0x173,"ANGLE");
        put(0x174,"ZOOM");
        put(0x175,"MODE");
        put(0x176,"KEYBOARD");
        put(0x177,"SCREEN");
        put(0x178,"PC");
        put(0x179,"TV");
        put(0x17a,"TV2");
        put(0x17b,"VCR");
        put(0x17c,"VCR2");
        put(0x17d,"SAT");
        put(0x17e,"SAT2");
        put(0x17f,"CD");
        put(0x180,"TAPE");
        put(0x181,"RADIO");
        put(0x182,"TUNER");
        put(0x183,"PLAYER");
        put(0x184,"TEXT");
        put(0x185,"DVD");
        put(0x186,"AUX");
        put(0x187,"MP3");
        put(0x188,"AUDIO");
        put(0x189,"VIDEO");
        put(0x18a,"DIRECTORY");
        put(0x18b,"LIST");
        put(0x18c,"MEMO");
        put(0x18d,"CALENDAR");
        put(0x18e,"RED");
        put(0x18f,"GREEN");
        put(0x190,"YELLOW");
        put(0x191,"BLUE");
        put(0x192,"CHANNELUP");
        put(0x193,"CHANNELDOWN");
        put(0x194,"FIRST");
        put(0x195,"LAST");
        put(0x196,"AB");
        put(0x197,"NEXT");
        put(0x198,"RESTART");
        put(0x199,"SLOW");
        put(0x19a,"SHUFFLE");
        put(0x19b,"BREAK");
        put(0x19c,"PREVIOUS");
        put(0x19d,"DIGITS");
        put(0x19e,"TEEN");
        put(0x19f,"TWEN");
        put(0x1a0,"VIDEOPHONE");
        put(0x1a1,"GAMES");
        put(0x1a2,"ZOOMIN");
        put(0x1a3,"ZOOMOUT");
        put(0x1a4,"ZOOMRESET");
        put(0x1a5,"WORDPROCESSOR");
        put(0x1a6,"EDITOR");
        put(0x1a7,"SPREADSHEET");
        put(0x1a8,"GRAPHICSEDITOR");
        put(0x1a9,"PRESENTATION");
        put(0x1aa,"DATABASE");
        put(0x1ab,"NEWS");
        put(0x1ac,"VOICEMAIL");
        put(0x1ad,"ADDRESSBOOK");
        put(0x1ae,"MESSENGER");
        put(0x1af,"DISPLAYTOGGLE");
        put(0x1b0,"SPELLCHECK");
        put(0x1b1,"LOGOFF");
        put(0x1b2,"DOLLAR");
        put(0x1b3,"EURO");
        put(0x1b4,"FRAMEBACK");
        put(0x1b5,"FRAMEFORWARD");
        put(0x1b6,"CONTEXT_MENU");
        put(0x1b7,"MEDIA_REPEAT");
        put(0x1b8,"10CHANNELSUP");
        put(0x1b9,"10CHANNELSDOWN");
        put(0x1ba,"IMAGES");
        put(0x1c0,"DEL_EOL");
        put(0x1c1,"DEL_EOS");
        put(0x1c2,"INS_LINE");
        put(0x1c3,"DEL_LINE");
        put(0x1d0,"FN");
        put(0x1d1,"FN_ESC");
        put(0x1d2,"FN_F1");
        put(0x1d3,"FN_F2");
        put(0x1d4,"FN_F3");
        put(0x1d5,"FN_F4");
        put(0x1d6,"FN_F5");
        put(0x1d7,"FN_F6");
        put(0x1d8,"FN_F7");
        put(0x1d9,"FN_F8");
        put(0x1da,"FN_F9");
        put(0x1db,"FN_F10");
        put(0x1dc,"FN_F11");
        put(0x1dd,"FN_F12");
        put(0x1de,"FN_1");
        put(0x1df,"FN_2");
        put(0x1e0,"FN_D");
        put(0x1e1,"FN_E");
        put(0x1e2,"FN_F");
        put(0x1e3,"FN_S");
        put(0x1e4,"FN_B");
        put(0x1f1,"BRL_DOT1");
        put(0x1f2,"BRL_DOT2");
        put(0x1f3,"BRL_DOT3");
        put(0x1f4,"BRL_DOT4");
        put(0x1f5,"BRL_DOT5");
        put(0x1f6,"BRL_DOT6");
        put(0x1f7,"BRL_DOT7");
        put(0x1f8,"BRL_DOT8");
        put(0x1f9,"BRL_DOT9");
        put(0x1fa,"BRL_DOT10");
        put(0x200,"NUMERIC_0");
        put(0x201,"NUMERIC_1");
        put(0x202,"NUMERIC_2");
        put(0x203,"NUMERIC_3");
        put(0x204,"NUMERIC_4");
        put(0x205,"NUMERIC_5");
        put(0x206,"NUMERIC_6");
        put(0x207,"NUMERIC_7");
        put(0x208,"NUMERIC_8");
        put(0x209,"NUMERIC_9");
        put(0x20a,"NUMERIC_STAR");
        put(0x20b,"NUMERIC_POUND");
        put(0x20c,"NUMERIC_A");
        put(0x20d,"NUMERIC_B");
        put(0x20e,"NUMERIC_C");
        put(0x20f,"NUMERIC_D");
        put(0x210,"CAMERA_FOCUS");
        put(0x211,"WPS_BUTTON");
        put(0x212,"TOUCHPAD_TOGGLE");
        put(0x213,"TOUCHPAD_ON");
        put(0x214,"TOUCHPAD_OFF");
        put(0x215,"CAMERA_ZOOMIN");
        put(0x216,"CAMERA_ZOOMOUT");
        put(0x217,"CAMERA_UP");
        put(0x218,"CAMERA_DOWN");
        put(0x219,"CAMERA_LEFT");
        put(0x21a,"CAMERA_RIGHT");
        put(0x21b,"ATTENDANT_ON");
        put(0x21c,"ATTENDANT_OFF");
        put(0x21d,"ATTENDANT_TOGGLE");
        put(0x21e,"LIGHTS_TOGGLE");
        put(0x230,"ALS_TOGGLE");
        put(0x240,"BUTTONCONFIG");
        put(0x241,"TASKMANAGER");
        put(0x242,"JOURNAL");
        put(0x243,"CONTROLPANEL");
        put(0x244,"APPSELECT");
        put(0x245,"SCREENSAVER");
        put(0x246,"VOICECOMMAND");
        put(0x247,"ASSISTANT");
        put(0x250,"BRIGHTNESS_MIN");
        put(0x251,"BRIGHTNESS_MAX");
        put(0x260,"KBDINPUTASSIST_PREV");
        put(0x261,"KBDINPUTASSIST_NEXT");
        put(0x262,"KBDINPUTASSIST_PREVGROUP");
        put(0x263,"KBDINPUTASSIST_NEXTGROUP");
        put(0x264,"KBDINPUTASSIST_ACCEPT");
        put(0x265,"KBDINPUTASSIST_CANCEL");
        put(0x266,"RIGHT_UP");
        put(0x267,"RIGHT_DOWN");
        put(0x268,"LEFT_UP");
        put(0x269,"LEFT_DOWN");
        put(0x26a,"ROOT_MENU");
        put(0x26b,"MEDIA_TOP_MENU");
        put(0x26c,"NUMERIC_11");
        put(0x26d,"NUMERIC_12");
        put(0x26e,"AUDIO_DESC");
        put(0x26f,"3D_MODE");
        put(0x270,"NEXT_FAVORITE");
        put(0x271,"STOP_RECORD");
        put(0x272,"PAUSE_RECORD");
        put(0x273,"VOD");
        put(0x274,"UNMUTE");
        put(0x275,"FASTREVERSE");
        put(0x276,"SLOWREVERSE");
        put(0x277,"DATA");
        put(0x278,"ONSCREEN_KEYBOARD");
        put(113,"MIN_INTERESTING");
        put(0x2ff,"MAX");
        put(0x100,"MISC");
        put(0x100,"0");
        put(0x101,"1");
        put(0x102,"2");
        put(0x103,"3");
        put(0x104,"4");
        put(0x105,"5");
        put(0x106,"6");
        put(0x107,"7");
        put(0x108,"8");
        put(0x109,"9");
        put(0x110,"MOUSE");
        put(0x110,"LEFT");
        put(0x111,"RIGHT");
        put(0x112,"MIDDLE");
        put(0x113,"SIDE");
        put(0x114,"EXTRA");
        put(0x115,"FORWARD");
        put(0x116,"BACK");
        put(0x117,"TASK");
        put(0x120,"JOYSTICK");
        put(0x120,"TRIGGER");
        put(0x121,"THUMB");
        put(0x122,"THUMB2");
        put(0x123,"TOP");
        put(0x124,"TOP2");
        put(0x125,"PINKIE");
        put(0x126,"BASE");
        put(0x127,"BASE2");
        put(0x128,"BASE3");
        put(0x129,"BASE4");
        put(0x12a,"BASE5");
        put(0x12b,"BASE6");
        put(0x12f,"DEAD");
        put(0x130,"GAMEPAD");
        put(0x130,"SOUTH");
        put(0x131,"EAST");
        put(0x132,"C");
        put(0x133,"NORTH");
        put(0x134,"WEST");
        put(0x135,"Z");
        put(0x136,"TL");
        put(0x137,"TR");
        put(0x138,"TL2");
        put(0x139,"TR2");
        put(0x13a,"SELECT");
        put(0x13b,"START");
        put(0x13c,"MODE");
        put(0x13d,"THUMBL");
        put(0x13e,"THUMBR");
        put(0x140,"DIGI");
        put(0x140,"TOOL_PEN");
        put(0x141,"TOOL_RUBBER");
        put(0x142,"TOOL_BRUSH");
        put(0x143,"TOOL_PENCIL");
        put(0x144,"TOOL_AIRBRUSH");
        put(0x145,"TOOL_FINGER");
        put(0x146,"TOOL_MOUSE");
        put(0x147,"TOOL_LENS");
        put(0x148,"TOOL_QUINTTAP");
        put(0x149,"STYLUS3");
        put(0x14a,"TOUCH");
        put(0x14b,"STYLUS");
        put(0x14c,"STYLUS2");
        put(0x14d,"TOOL_DOUBLETAP");
        put(0x14e,"TOOL_TRIPLETAP");
        put(0x14f,"TOOL_QUADTAP");
        put(0x150,"WHEEL");
        put(0x150,"GEAR_DOWN");
        put(0x151,"GEAR_UP");
        put(0x220,"DPAD_UP");
        put(0x221,"DPAD_DOWN");
        put(0x222,"DPAD_LEFT");
        put(0x223,"DPAD_RIGHT");
        put(0x2c0,"TRIGGER_HAPPY");
        put(0x2c0,"TRIGGER_HAPPY1");
        put(0x2c1,"TRIGGER_HAPPY2");
        put(0x2c2,"TRIGGER_HAPPY3");
        put(0x2c3,"TRIGGER_HAPPY4");
        put(0x2c4,"TRIGGER_HAPPY5");
        put(0x2c5,"TRIGGER_HAPPY6");
        put(0x2c6,"TRIGGER_HAPPY7");
        put(0x2c7,"TRIGGER_HAPPY8");
        put(0x2c8,"TRIGGER_HAPPY9");
        put(0x2c9,"TRIGGER_HAPPY10");
        put(0x2ca,"TRIGGER_HAPPY11");
        put(0x2cb,"TRIGGER_HAPPY12");
        put(0x2cc,"TRIGGER_HAPPY13");
        put(0x2cd,"TRIGGER_HAPPY14");
        put(0x2ce,"TRIGGER_HAPPY15");
        put(0x2cf,"TRIGGER_HAPPY16");
        put(0x2d0,"TRIGGER_HAPPY17");
        put(0x2d1,"TRIGGER_HAPPY18");
        put(0x2d2,"TRIGGER_HAPPY19");
        put(0x2d3,"TRIGGER_HAPPY20");
        put(0x2d4,"TRIGGER_HAPPY21");
        put(0x2d5,"TRIGGER_HAPPY22");
        put(0x2d6,"TRIGGER_HAPPY23");
        put(0x2d7,"TRIGGER_HAPPY24");
        put(0x2d8,"TRIGGER_HAPPY25");
        put(0x2d9,"TRIGGER_HAPPY26");
        put(0x2da,"TRIGGER_HAPPY27");
        put(0x2db,"TRIGGER_HAPPY28");
        put(0x2dc,"TRIGGER_HAPPY29");
        put(0x2dd,"TRIGGER_HAPPY30");
        put(0x2de,"TRIGGER_HAPPY31");
        put(0x2df,"TRIGGER_HAPPY32");
        put(0x2e0,"TRIGGER_HAPPY33");
        put(0x2e1,"TRIGGER_HAPPY34");
        put(0x2e2,"TRIGGER_HAPPY35");
        put(0x2e3,"TRIGGER_HAPPY36");
        put(0x2e4,"TRIGGER_HAPPY37");
        put(0x2e5,"TRIGGER_HAPPY38");
        put(0x2e6,"TRIGGER_HAPPY39");
        put(0x2e7,"TRIGGER_HAPPY40");
    }};

    public final String source;
    public final int keycode;
    public final boolean isKeydown;

    public static final Parcelable.Creator<KeypressEvent> CREATOR =
        new Parcelable.Creator<KeypressEvent>() {
            public KeypressEvent createFromParcel(Parcel in) {
                return new KeypressEvent(in);
            }

            public KeypressEvent[] newArray(int size) {
                return new KeypressEvent[size];
            }
        };

    public KeypressEvent(Parcel in) {
        source = in.readString();
        keycode = in.readInt();
        isKeydown = (in.readInt() != 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(source);
        dest.writeInt(keycode);
        dest.writeInt(isKeydown ? 1 : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof KeypressEvent) {
            KeypressEvent other = (KeypressEvent)o;
            return other.source.equals(source) &&
                    other.keycode == keycode &&
                    other.isKeydown == isKeydown;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, keycode, isKeydown);
    }

    @Override
    public String toString() {
        return"Event{source = " + source + ", keycode = " + keycode +
                ", isKeydown = " + isKeydown + "}";
    }

    public String keycodeToString() {
        return keycodeToString(keycode);
    }

    /**
     * Translates a key code from keventreader into a string.
     * @param keycode Key code from a keventreader KeypressEvent.
     * @return String String label corresponding to keycode, if available. If not, String with
     *     hexidecimal representation of keycode.
     */
    public static String keycodeToString(int keycode) {
        String ret = KEYCODE_NAME_MAP.get(keycode);
        return ret != null ? ret : "0x" + Integer.toHexString(keycode);
    }
}
