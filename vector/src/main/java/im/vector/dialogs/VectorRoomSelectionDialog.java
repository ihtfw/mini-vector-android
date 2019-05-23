package im.vector.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.design.widget.TextInputEditText;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;

import java.util.List;
import java.util.regex.Pattern;

import im.vector.R;
import im.vector.adapters.VectorRoomsSelectionAdapter;

public class VectorRoomSelectionDialog extends Dialog implements DialogInterface.OnClickListener {

    public interface RoomSummarySelectedListener {
        void onRoomSummarySelected(RoomSummary roomSummary);
    }

    private RoomSummarySelectedListener listener;
    private Context context;
    private MXSession session;
    private final List<RoomSummary> roomSummaries;
    private ListView list;
    private TextInputEditText filterText = null;
    VectorRoomsSelectionAdapter adapter = null;
    private static final String TAG = "VectorRoomList";

    public VectorRoomSelectionDialog(Context context, MXSession session, List<RoomSummary> roomSummaries) {
        super(context);
        this.context = context;
        this.session = session;
        this.roomSummaries = roomSummaries;

        /** Design the dialog in main.xml file */
        setContentView(R.layout.dialog_select_room);
        this.setTitle("Select Room");
        filterText = findViewById(R.id.search_text_input_edit_box);
        filterText.addTextChangedListener(filterTextWatcher);
        list = findViewById(R.id.List);
        this.adapter = new VectorRoomsSelectionAdapter(context, R.layout.adapter_item_vector_recent_room, session);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            RoomSummary roomSummary = (RoomSummary)list.getItemAtPosition(position);
            listener.onRoomSummarySelected(roomSummary);
        });

        this.adapter.addAll(roomSummaries);
    }

    public void setOnRoomSummarySelectedListener(RoomSummarySelectedListener listener){
        this.listener = listener;
    }

    private TextWatcher filterTextWatcher = new TextWatcher() {

        public void afterTextChanged(Editable s) {
        }

        public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {
        }

        public void onTextChanged(CharSequence constraint, int start, int before,
                                  int count) {
            adapter.clear();

            if (TextUtils.isEmpty(constraint)) {
                adapter.addAll(roomSummaries);
            } else {
                final String filterPattern = constraint.toString().trim();

                for (final RoomSummary roomSummary : roomSummaries) {
                    Room room = session.getDataHandler().getRoom(roomSummary.getRoomId());
                    String roomName = room.getRoomDisplayName(context);

                    if (roomName == null)
                        continue;

                    if (Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE)
                            .matcher(roomName).find()) {
                        adapter.add(roomSummary);
                    }
                }
            }

            //adapter.getFilter().filter(s);
        }
    };
    @Override
    public void onStop(){
        filterText.removeTextChangedListener(filterTextWatcher);
        listener = null;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

    }
}
