package com.example.monitor.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.example.monitor.R;
import com.example.monitor.models.Weather;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/* RecyclerView is populated by the OS using these callback methods */
public class RecyclerWeatherAdapter extends RecyclerView.Adapter<RecyclerWeatherAdapter.WeatherViewHolder> {
    private List<Weather> weatherRecyclerEntries = new ArrayList<>();

    @NonNull
    @Override
    public WeatherViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_listitem, parent, false);
        return new WeatherViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull WeatherViewHolder holder, int position) {
        Weather currentWeatherPoint = weatherRecyclerEntries.get(position);
        holder.timeText.setText(currentWeatherPoint.getTime());
        holder.timeMillis.setText(Long.toString(currentWeatherPoint.getTimeInMillis()));

        /* extract the hour */
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(currentWeatherPoint.getTimeInMillis());
        holder.timeHours.setText(Integer.toString(today.get(Calendar.HOUR)));

        holder.temperatureText.setText(currentWeatherPoint.getCelsius() + " C");

        /* don't bind a value to link field for debugging; it takes a lot of space in RecyclerView */
//        holder.linkText.setText(currentWeatherPoint.getLink());
    }

    @Override
    public int getItemCount() {
        return weatherRecyclerEntries.size() - 1;
    }

    public void setWeatherRecyclerEntries(List<Weather> weatherRecyclerEntries) {
        this.weatherRecyclerEntries = weatherRecyclerEntries;
        notifyDataSetChanged();
        /* also use notifyItemInserted, notifyItemRemoved if needed */
    }


    /* the holder class */
    public class WeatherViewHolder extends RecyclerView.ViewHolder {
        TextView timeText;
        TextView timeMillis;
        TextView timeHours;
        TextView temperatureText;
//        TextView linkText;

        ConstraintLayout entryParentLayout;
        public WeatherViewHolder(@NonNull View itemView) {
            super(itemView);
            timeText = itemView.findViewById(R.id.idTimeRaw);
            timeMillis = itemView.findViewById(R.id.idTimeMillis);
            timeHours = itemView.findViewById(R.id.idTimeHours);

            temperatureText = itemView.findViewById(R.id.idTemp);
//            linkText = itemView.findViewById(R.id.idLinkInfo);
            entryParentLayout = itemView.findViewById(R.id.idListEntryContainer);
        }
    }
}
