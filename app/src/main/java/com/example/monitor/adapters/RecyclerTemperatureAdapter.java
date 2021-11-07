package com.example.monitor.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.example.monitor.R;
import com.example.monitor.models.Temperature;

import java.util.ArrayList;

/* RecyclerView is populated by the OS using these callback methods */
public class RecyclerTemperatureAdapter extends RecyclerView.Adapter<RecyclerTemperatureAdapter.ViewHolder> {

    private static final String TAG = "RecyclerTemperatureAdapter";

    private ArrayList<Temperature> tempDataEntries/* = new ArrayList<>()*/;

    private Context context;

    public RecyclerTemperatureAdapter(ArrayList<Temperature> tempDataEntries, Context context) {
        this.tempDataEntries = tempDataEntries;
        this.context = context;
    }

    /* prepares each item by inflating the xml */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_listitem, parent, false);
        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    /* called per list element, indexed by position */
    @SuppressLint("LongLogTag")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Log.d(TAG, "onBindViewHolder: called");

        holder.timeText.setText(tempDataEntries.get(position).getTime());
        holder.temperatureText.setText(tempDataEntries.get(position).getCelsius());
        holder.linkText.setText(tempDataEntries.get(position).getLink());

    }

    @Override
    public int getItemCount() {
        return tempDataEntries.size();
    }

    /* ViewHolder object maps to the display representation of each element in a passed list item (view) */
    public class ViewHolder extends RecyclerView.ViewHolder {

        TextView timeText;
        TextView temperatureText;
        TextView linkText;
        ConstraintLayout entryParentLayout;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            timeText = itemView.findViewById(R.id.idTime);
            temperatureText = itemView.findViewById(R.id.idTemp);
            linkText = itemView.findViewById(R.id.idLinkInfo);
            entryParentLayout = itemView.findViewById(R.id.idListEntryContainer);

        }
    }
}
