package com.example.guitartuner;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SongbookFragment extends Fragment {

    private RecyclerView recyclerView;
    private SongAdapter adapter;
    private ScrollView songDetailView;
    private TextView detailTitle;
    private TextView detailLyrics;
    private ImageButton btnBackToList;
    private ImageButton btnBackToTuner;
    private TextView songbookTitle;
    private View songbookHeader;

    private String currentPath = "songs";
    private final List<Song> displayedItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_songbook, container, false);

        recyclerView = view.findViewById(R.id.songsRecyclerView);
        songDetailView = view.findViewById(R.id.songDetailView);
        detailTitle = view.findViewById(R.id.detailSongTitle);
        detailLyrics = view.findViewById(R.id.detailSongLyrics);
        btnBackToList = view.findViewById(R.id.btnBackToList);
        btnBackToTuner = view.findViewById(R.id.btnBackToTuner);
        songbookTitle = view.findViewById(R.id.songbookTitle);
        songbookHeader = view.findViewById(R.id.songbookHeader);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new SongAdapter(displayedItems, song -> {
            if (song.isFolder()) {
                navigateToPath(song.getPath());
            } else {
                showSongDetail(song);
            }
        });
        recyclerView.setAdapter(adapter);

        navigateToPath("songs");

        btnBackToTuner.setOnClickListener(v -> {
            if (currentPath.equals("songs")) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).switchToTuner();
                }
            } else {
                // Pojdi en nivo gor
                String parentPath = currentPath.substring(0, currentPath.lastIndexOf("/"));
                navigateToPath(parentPath);
            }
        });

        btnBackToList.setOnClickListener(v -> {
            songDetailView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            songbookHeader.setVisibility(View.VISIBLE);
        });

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisibility(View.GONE);
        }

        return view;
    }

    private void navigateToPath(String path) {
        currentPath = path;
        displayedItems.clear();
        
        // Nastavimo naslov glede na mapo
        if (path.equals("songs")) {
            songbookTitle.setText("Pesmarica");
        } else {
            String folderName = path.substring(path.lastIndexOf("/") + 1);
            songbookTitle.setText(folderName.substring(0, 1).toUpperCase() + folderName.substring(1));
        }

        AssetManager assetManager = requireContext().getAssets();
        try {
            String[] items = assetManager.list(path);
            if (items != null) {
                for (String item : items) {
                    String fullPath = path + "/" + item;
                    if (!item.contains(".")) {
                        // Mapa
                        displayedItems.add(new Song(item, "", "", fullPath, true));
                    } else if (item.endsWith(".txt")) {
                        // Pesem
                        Song song = parseSongFile(fullPath);
                        if (song != null) {
                            displayedItems.add(song);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        adapter.notifyDataSetChanged();
    }

    private Song parseSongFile(String path) {
        AssetManager assetManager = requireContext().getAssets();
        try (InputStream is = assetManager.open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            String title = reader.readLine();
            String artist = reader.readLine();
            
            StringBuilder lyricsBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                lyricsBuilder.append(line).append("\n");
            }
            
            if (title != null && artist != null) {
                return new Song(title, artist, lyricsBuilder.toString().trim(), path, false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void showSongDetail(Song song) {
        detailTitle.setText(song.getTitle());
        detailLyrics.setText(song.getLyrics());
        songDetailView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        songbookHeader.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisibility(View.VISIBLE);
        }
    }
}
