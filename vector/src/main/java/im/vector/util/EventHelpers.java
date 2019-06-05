package im.vector.util;

import android.support.annotation.ColorRes;

import im.vector.R;

public class EventHelpers {
    //Based on riot-web implementation
    @ColorRes
    public static int colorIndexForSender(String sender) {
        int hash = 0;
        int i;
        char chr;
        if (sender.length() == 0) {
            return R.color.username_1;
        }
        for (i = 0; i < sender.length(); i++) {
            chr = sender.charAt(i);
            hash = ((hash << 5) - hash) + chr;
            hash |= 0;
        }
        int cI = (Math.abs(hash) % 8) + 1;
        switch (cI) {
            case 1:
                return R.color.username_1;
            case 2:
                return R.color.username_2;
            case 3:
                return R.color.username_3;
            case 4:
                return R.color.username_4;
            case 5:
                return R.color.username_5;
            case 6:
                return R.color.username_6;
            case 7:
                return R.color.username_7;
            default:
                return R.color.username_8;
        }
    }
}
