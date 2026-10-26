package com.example.it_inventoryeq.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.it_inventoryeq.R;
import com.example.it_inventoryeq.models.ScanLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Adapter for Recent Live Scans Feed
 * Styled with unified badge colors and 12dp rounded cards.
 */
public class ScanLogAdapter extends RecyclerView.Adapter<ScanLogAdapter.ScanLogViewHolder> {

    private final Context context;
    private List<ScanLog> scanLogs = new ArrayList<>();
    private final SimpleDateFormat isoParser;
    private final SimpleDateFormat displayFormat;

    public ScanLogAdapter(Context context) {
        this.context = context;
        this.isoParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        this.isoParser.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.displayFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    }

    public void setScanLogs(List<ScanLog> logs) {
        this.scanLogs = logs != null ? logs : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addScanLogAtTop(ScanLog log) {
        if (log != null) {
            this.scanLogs.add(0, log);
            notifyItemInserted(0);
        }
    }

    @NonNull
    @Override
    public ScanLogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_scan_log, parent, false);
        return new ScanLogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScanLogViewHolder holder, int position) {
        ScanLog log = scanLogs.get(position);

        holder.tvAssetTag.setText(log.getAssetTag());
        holder.tvItemName.setText(log.getItemName());
        
        String loc = log.getLocation();
        if (loc == null || loc.trim().isEmpty()) {
            loc = "IT Storage";
        }
        holder.tvLocation.setText("📍 " + loc);

        // Format Timestamp
        holder.tvTimestamp.setText(formatTime(log.getTimestamp()));

        // Remarks
        String remarks = log.getRemarks();
        if (remarks != null && !remarks.trim().isEmpty()) {
            holder.tvRemarks.setVisibility(View.VISIBLE);
            holder.tvRemarks.setText(remarks);
        } else {
            holder.tvRemarks.setVisibility(View.GONE);
        }

        // Apply Unified Status Pill Badges
        applyActionBadge(holder.tvStatusBadge, log.getActionType());
    }

    private void applyActionBadge(TextView badgeView, String action) {
        if (action == null) action = "Audit Verify";
        String actLower = action.toLowerCase();

        if (actLower.contains("defective")) {
            badgeView.setText("DEFECTIVE");
            badgeView.setBackgroundResource(R.drawable.bg_badge_defective);
            badgeView.setTextColor(ContextCompat.getColor(context, R.color.status_defective));
        } else if (actLower.contains("check-out") || actLower.contains("assign")) {
            badgeView.setText("ASSIGNED");
            badgeView.setBackgroundResource(R.drawable.bg_badge_assigned);
            badgeView.setTextColor(ContextCompat.getColor(context, R.color.status_assigned));
        } else if (actLower.contains("maintenance")) {
            badgeView.setText("MAINTENANCE");
            badgeView.setBackgroundResource(R.drawable.bg_badge_maintenance);
            badgeView.setTextColor(ContextCompat.getColor(context, R.color.status_maintenance));
        } else {
            // Audit Verify / Working / Available / New Registration
            badgeView.setText(actLower.contains("reg") ? "REGISTERED" : "VERIFIED");
            badgeView.setBackgroundResource(R.drawable.bg_badge_available);
            badgeView.setTextColor(ContextCompat.getColor(context, R.color.status_available));
        }
    }

    private String formatTime(String isoString) {
        if (isoString == null) return "Just now";
        try {
            // Clean decimal seconds if present
            String clean = isoString;
            if (clean.contains(".")) {
                clean = clean.substring(0, clean.indexOf("."));
            }
            Date date = isoParser.parse(clean);
            if (date != null) {
                return displayFormat.format(date);
            }
        } catch (Exception ignored) {}
        return "Recent";
    }

    @Override
    public int getItemCount() {
        return scanLogs.size();
    }

    static class ScanLogViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAssetTag;
        final TextView tvStatusBadge;
        final TextView tvItemName;
        final TextView tvLocation;
        final TextView tvTimestamp;
        final TextView tvRemarks;

        ScanLogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAssetTag = itemView.findViewById(R.id.tvLogAssetTag);
            tvStatusBadge = itemView.findViewById(R.id.tvLogStatusBadge);
            tvItemName = itemView.findViewById(R.id.tvLogItemName);
            tvLocation = itemView.findViewById(R.id.tvLogLocation);
            tvTimestamp = itemView.findViewById(R.id.tvLogTimestamp);
            tvRemarks = itemView.findViewById(R.id.tvLogRemarks);
        }
    }
}
