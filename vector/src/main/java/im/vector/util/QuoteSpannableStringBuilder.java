package im.vector.util;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.util.JsonUtils;

import im.vector.R;
import im.vector.ui.VectorQuoteSpan;

public class QuoteSpannableStringBuilder {
    public int senderColor = -1;

    public SpannableString Build(Context context, Room mRoom, Event mEvent){
        String mSenderDisplayName = mEvent.getSender();
        RoomState roomState = mRoom.getState();
        if (roomState != null) {
            mSenderDisplayName = roomState.getMemberName(mEvent.getSender());
        }

        String quoteText = "";

        Message quoteMessage = JsonUtils.toMessage(mEvent.getContentAsJsonObject());
        if (quoteMessage.body != null){
            quoteText = quoteMessage.body;
        }

        if (quoteText.length() > 0){
            SpannableString spannableString = new SpannableString(mSenderDisplayName + "\n" + quoteText);
            spannableString.setSpan(new VectorQuoteSpan(context), 0, spannableString.length(), 0);

            if (senderColor < 0){
                senderColor = ContextCompat.getColor(context, R.color.riot_text_quote_sender_color_default);
            }

            spannableString.setSpan(new ForegroundColorSpan(senderColor), 0, mSenderDisplayName.length(), 0);
            spannableString.setSpan(new AbsoluteSizeSpan(14, true), 0, mSenderDisplayName.length(), 0);

            return spannableString;
        }

        return new SpannableString("");
    }
}
