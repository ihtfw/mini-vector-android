/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.util;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.interfaces.HtmlToolbox;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.pid.RoomThirdPartyInvite;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.widgets.WidgetContent;
import im.vector.widgets.WidgetsManager;

public class RiotEventDisplay extends EventDisplay {
    private static final String LOG_TAG = RiotEventDisplay.class.getSimpleName();

    private static final Map<String, Event> mClosingWidgetEventByStateKey = new HashMap<>();

    // constructor
    public RiotEventDisplay(Context context, HtmlToolbox htmlToolbox) {
        super(context, htmlToolbox);
    }

    // constructor
    public RiotEventDisplay(Context context) {
        super(context);
    }

    /**
     * Stringify the linked event.
     *
     * @param displayNameColor the display name highlighted color.
     * @return The text or null if it isn't possible.
     */
    @Override
    public CharSequence getTextualDisplay(Integer displayNameColor, Event event, RoomState roomState) {
        CharSequence text = null;

        try {
            if (TextUtils.equals(event.getType(), WidgetsManager.WIDGET_EVENT_TYPE)) {
                JsonObject content = event.getContentAsJsonObject();

                EventContent eventContent = JsonUtils.toEventContent(event.getContentAsJsonObject());
                EventContent prevEventContent = event.getPrevContent();
                String senderDisplayName = senderDisplayNameForEvent(event, eventContent, prevEventContent, roomState);

                if (0 == content.entrySet().size()) {
                    Event closingWidgetEvent = mClosingWidgetEventByStateKey.get(event.stateKey);

                    if (null == closingWidgetEvent) {
                        List<Event> widgetEvents = roomState.getStateEvents(new HashSet<>(Arrays.asList(WidgetsManager.WIDGET_EVENT_TYPE)));

                        for (Event widgetEvent : widgetEvents) {
                            if (TextUtils.equals(widgetEvent.stateKey, event.stateKey) && !widgetEvent.getContentAsJsonObject().entrySet().isEmpty()) {
                                closingWidgetEvent = widgetEvent;
                                break;
                            }
                        }

                        if (null != closingWidgetEvent) {
                            mClosingWidgetEventByStateKey.put(event.stateKey, closingWidgetEvent);
                        }
                    }

                    String type = (null != closingWidgetEvent) ?
                            WidgetContent.toWidgetContent(closingWidgetEvent.getContentAsJsonObject()).getHumanName() : "undefined";
                    text = mContext.getString(R.string.event_formatter_widget_removed, type, senderDisplayName);
                } else {
                    String type = WidgetContent.toWidgetContent(event.getContentAsJsonObject()).getHumanName();
                    text = mContext.getString(R.string.event_formatter_widget_added, type, senderDisplayName);
                }
            } else {
                text = getQuoteTextualDisplay(displayNameColor, event, roomState);
            }
            if (event.getCryptoError() != null) {
                final MXSession session = Matrix.getInstance(mContext).getDefaultSession();
                VectorApp.getInstance()
                        .getDecryptionFailureTracker()
                        .reportUnableToDecryptError(event, roomState, session.getMyUserId());
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "getTextualDisplay() " + e.getMessage(), e);
        }

        return text;
    }

    /**
     * Stringify the linked event.
     *
     * @param displayNameColor the display name highlighted color.
     * @return The text or null if it isn't possible.
     */
    public CharSequence getQuoteTextualDisplay(Integer displayNameColor, Event event, RoomState roomState) {
        if (!Event.EVENT_TYPE_MESSAGE.equals(event.getType())){
            //can't be quote
            return super.getTextualDisplay(displayNameColor, event, roomState);
        }

        try {
            JsonObject jsonEventContent = event.getContentAsJsonObject();

            // Special treatment for "In reply to" message
            if (jsonEventContent.has("m.relates_to")) {
                final JsonElement relatesTo = jsonEventContent.get("m.relates_to");
                if (relatesTo.isJsonObject()) {
                    if (relatesTo.getAsJsonObject().has("m.in_reply_to")) {
                        return getQuoteFormattedMessage(mContext, jsonEventContent, mHtmlToolbox);
                    }
                }
            }

            //not a quote
        } catch (Exception e) {
            Log.e(LOG_TAG, "getTextualDisplay() " + e.getMessage(), e);
        }

        return super.getTextualDisplay(displayNameColor, event, roomState);
    }

    /**
     * @param context          the context
     * @param jsonEventContent the current jsonEventContent
     * @param htmlToolbox      an optional htmlToolbox to manage html images and tag
     * @return the formatted message as CharSequence
     */
    private CharSequence getQuoteFormattedMessage(@NonNull final Context context,
                                             @NonNull final JsonObject jsonEventContent,
                                             @Nullable final HtmlToolbox htmlToolbox) {
        final String format = jsonEventContent.getAsJsonPrimitive("format").getAsString();
        CharSequence text = null;
        if (Message.FORMAT_MATRIX_HTML.equals(format)) {
            String htmlBody = jsonEventContent.getAsJsonPrimitive("formatted_body").getAsString();
            //skip quote part
            int i = htmlBody.indexOf("</mx-reply>");
            if (i > 0){
                htmlBody = htmlBody.substring(i + "</mx-reply>".length());
            }

            if (htmlToolbox != null) {
                htmlBody = htmlToolbox.convert(htmlBody);
            }

            // some markers are not supported so fallback on an ascii display until to find the right way to manage them
            // an issue has been created https://github.com/vector-im/vector-android/issues/38
            // BMA re-enable <ol> and <li> support (https://github.com/vector-im/riot-android/issues/2184)
            if (!TextUtils.isEmpty(htmlBody)) {
                final Html.ImageGetter imageGetter;
                final Html.TagHandler tagHandler;
                if (htmlToolbox != null) {
                    imageGetter = htmlToolbox.getImageGetter();
                    tagHandler = htmlToolbox.getTagHandler(htmlBody);
                } else {
                    imageGetter = null;
                    tagHandler = null;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    text = Html.fromHtml(htmlBody,
                            Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM | Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST,
                            imageGetter, tagHandler);
                } else {
                    text = Html.fromHtml(htmlBody, imageGetter, tagHandler);
                }
                // fromHtml formats quotes (> character) with two newlines at the end
                // remove any newlines at the end of the CharSequence
                while (text.length() > 0 && text.charAt(text.length() - 1) == '\n') {
                    text = text.subSequence(0, text.length() - 1);
                }
            }
        }
        return text;
    }
}
