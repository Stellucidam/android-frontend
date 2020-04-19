package ch.heigvd.pro.b04.android.Home;

import android.graphics.Color;
import android.media.Image;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.Set;

import ch.heigvd.pro.b04.android.R;

public class EmojiAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_EMOJI = 1;

    private HomeViewModel state;
    private Set<Integer> selected = new HashSet<>();

    public EmojiAdapter(HomeViewModel state, LifecycleOwner lifecycleOwner) {
        this.state = state;

        state.getSelectedEmoji().observe(lifecycleOwner, emojis -> {
            selected.clear();
            for (Emoji emoji : emojis) {
                selected.add(emoji.ordinal() + 1);
            }
            notifyDataSetChanged();
        });
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_HEADER:
                return new HeaderViewHolder(parent);
            case VIEW_TYPE_EMOJI:
                return new EmojiViewHolder(parent);
            default:
                throw new IllegalStateException("Unknown view type.");
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (position) {
            case 0:
                ((HeaderViewHolder) holder).setTitle(
                        state.getSelectedEmoji()
                );
                break;
            default:
                ((EmojiViewHolder) holder).bindEmoji(
                        Emoji.values()[position - 1],
                        selected.contains(position)
                );
                break;
        }
    }

    @Override
    public int getItemCount() {
        return Emoji.values().length + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0
                ? VIEW_TYPE_HEADER
                : VIEW_TYPE_EMOJI;
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private TextView title;

        public HeaderViewHolder(@NonNull ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.home_title, parent, false));
            title = itemView.findViewById(R.id.home_title_emoji);
        }

        private void setTitle(LiveData<Set<Emoji>> emoji) {
            if(emoji.getValue() == null) {
                title.setText(R.string.home_welcome);
                return;
            }
            title.setText("");
        }
    }

    private class EmojiViewHolder extends RecyclerView.ViewHolder {
        private ImageButton emojiButton;

        private EmojiViewHolder(@NonNull ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.home_emoji, parent, false));

            emojiButton = itemView.findViewById(R.id.home_emoji_item);
        }

        private void bindEmoji(Emoji emoji, boolean selected) {
            emojiButton.setImageResource(emoji.getEmoji());
            emojiButton.setOnClickListener(v -> state.addNewEmoji(emoji));

            if (selected) {
                emojiButton.setBackgroundColor(Color.RED);
            } else {
                emojiButton.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }
}
