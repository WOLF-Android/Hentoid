package me.devsaki.hentoid.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.viewholders.AttributeViewHolder;

/**
 * Adapter for the available attributes list displayed in the advanced search screen
 */
public class AvailableAttributeAdapter extends RecyclerView.Adapter<AttributeViewHolder> {

    // Threshold for infinite loading
    private static final int VISIBLE_THRESHOLD = 5;

    private final List<Attribute> dataset = new ArrayList<>();
    private Runnable onScrollToEndListener = null;
    private View.OnClickListener onClickListener = null;
    private boolean formatWithNamespace = false;

    @NonNull
    @Override
    public AttributeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chip_choice, parent, false);
        return new AttributeViewHolder(view);
    }

    public void setOnScrollToEndListener(Runnable listener) {
        this.onScrollToEndListener = listener;
    }

    public void setOnClickListener(View.OnClickListener listener) {
        this.onClickListener = listener;
    }

    public void setFormatWithNamespace(boolean formatWithNamespace) {
        this.formatWithNamespace = formatWithNamespace;
    }

    @Override
    public void onBindViewHolder(@NonNull AttributeViewHolder holder, int position) {
        if (position == getItemCount() - VISIBLE_THRESHOLD && onScrollToEndListener != null) {
            onScrollToEndListener.run();
        }
        holder.bindTo(dataset.get(position), formatWithNamespace);
        holder.itemView.setOnClickListener(onClickListener);
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }

    public void add(List<Attribute> contents) {
        dataset.addAll(contents);
        notifyDataSetChanged();
    }

    public void clear() {
        dataset.clear();
        notifyDataSetChanged();
    }
}
