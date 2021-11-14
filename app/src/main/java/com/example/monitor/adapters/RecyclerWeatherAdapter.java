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

import java.util.ArrayList;
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
        holder.temperatureText.setText(currentWeatherPoint.getCelsius());
        holder.linkText.setText(currentWeatherPoint.getLink());
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

    public class WeatherViewHolder extends RecyclerView.ViewHolder {
        TextView timeText;
        TextView temperatureText;
        TextView linkText;
        ConstraintLayout entryParentLayout;
        public WeatherViewHolder(@NonNull View itemView) {
            super(itemView);
            timeText = itemView.findViewById(R.id.idTime);
            temperatureText = itemView.findViewById(R.id.idTemp);
            linkText = itemView.findViewById(R.id.idLinkInfo);
            entryParentLayout = itemView.findViewById(R.id.idListEntryContainer);
        }
    }
}
