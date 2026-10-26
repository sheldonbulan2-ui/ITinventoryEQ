package com.example.it_inventoryeq.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.it_inventoryeq.R;
import com.example.it_inventoryeq.database.LocalDatabaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for Offline Sync Queue list
 */
public class OfflineQueueAdapter extends RecyclerView.Adapter<OfflineQueueAdapter.QueueViewHolder> {

    private final Context context;
    private List<LocalDatabaseHelper.OfflineQueueItem> items = new ArrayList<>();

    public OfflineQueueAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<LocalDatabaseHelper.OfflineQueueItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_offline_queue, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        LocalDatabaseHelper.OfflineQueueItem item = items.get(position);

        holder.tvTag.setText(item.assetTag != null ? item.assetTag : "N/A");
        holder.tvName.setText(item.itemName != null && !item.itemName.isEmpty() ? item.itemName : "IT Equipment");
        holder.tvAction.setText("Action: " + (item.actionType != null ? item.actionType : "Audit Verify"));
        holder.tvLocation.setText("📍 " + (item.location != null ? item.location : "Storage"));

        if (item.remarks != null && !item.remarks.isEmpty()) {
            holder.tvRemarks.setVisibility(View.VISIBLE);
            holder.tvRemarks.setText(item.remarks);
        } else {
            holder.tvRemarks.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class QueueViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTag;
        final TextView tvName;
        final TextView tvAction;
        final TextView tvLocation;
        final TextView tvRemarks;

        QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTag = itemView.findViewById(R.id.tvQueueTag);
            tvName = itemView.findViewById(R.id.tvQueueItemName);
            tvAction = itemView.findViewById(R.id.tvQueueAction);
            tvLocation = itemView.findViewById(R.id.tvQueueLocation);
            tvRemarks = itemView.findViewById(R.id.tvQueueRemarks);
        }
    }
}
