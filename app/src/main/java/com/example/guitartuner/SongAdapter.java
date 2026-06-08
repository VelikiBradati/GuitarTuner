package com.example.guitartuner;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    private List<Song> songList;
    private OnSongClickListener listener;

    public interface OnSongClickListener {
        void onSongClick(Song song);
    }

    public SongAdapter(List<Song> songList, OnSongClickListener listener) {
        this.songList = songList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songList.get(position);
        holder.titleText.setText(song.getTitle());
        
        if (song.isFolder()) {
            holder.artistText.setVisibility(View.GONE);
            holder.iconView.setImageResource(android.R.drawable.ic_menu_directions); // Folder icon substitute
            holder.iconView.setVisibility(View.VISIBLE);
        } else {
            holder.artistText.setText(song.getArtist());
            holder.artistText.setVisibility(View.VISIBLE);
            holder.iconView.setVisibility(View.GONE);
        }
        
        holder.itemView.setOnClickListener(v -> listener.onSongClick(song));
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView artistText;
        ImageView iconView;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.songTitle);
            artistText = itemView.findViewById(R.id.songArtist);
            iconView = itemView.findViewById(R.id.songIcon);
        }
    }
}
