package com.example.guitartuner;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.Locale;

public class TunerFragment extends Fragment {

    private TextView statusText;
    private TextView noteText;
    private TextView freqText;
    private TextView rawLogsText;
    private ScrollView rawLogsScroll;
    private View tunerPointer;
    private LinearLayout waterfallContainer;
    private Button btnConnect;

    private TunerInterface tunerInterface;

    public interface TunerInterface {
        void onConnectClicked();
        boolean isConnected();
    }

    public void setTunerInterface(TunerInterface tunerInterface) {
        this.tunerInterface = tunerInterface;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tuner, container, false);

        statusText = view.findViewById(R.id.statusText);
        noteText = view.findViewById(R.id.noteText);
        freqText = view.findViewById(R.id.freqText);
        rawLogsText = view.findViewById(R.id.rawLogsText);
        rawLogsScroll = view.findViewById(R.id.rawLogsScroll);
        tunerPointer = view.findViewById(R.id.tunerPointer);
        waterfallContainer = view.findViewById(R.id.waterfallContainer);
        btnConnect = view.findViewById(R.id.btnConnect);

        btnConnect.setOnClickListener(v -> {
            if (tunerInterface != null) {
                tunerInterface.onConnectClicked();
            }
        });

        updateConnectButton(tunerInterface != null && tunerInterface.isConnected());

        return view;
    }

    public void updateConnectButton(boolean isConnected) {
        if (btnConnect == null) return;
        if (isConnected) {
            btnConnect.setText("Odklopi");
            btnConnect.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_dark));
        } else {
            btnConnect.setText("Poveži z XIAO");
            btnConnect.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.accentColor));
        }
    }

    public void setStatusText(String text) {
        if (statusText != null) statusText.setText(text);
    }

    public void setStatusTextColor(int colorRes) {
        if (statusText != null) statusText.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
    }

    public void setNoteText(String text) {
        if (noteText != null) noteText.setText(text);
    }

    public void setFreqText(String text) {
        if (freqText != null) freqText.setText(text);
    }

    public void appendLog(String text) {
        if (rawLogsText == null) return;
        String currentText = rawLogsText.getText().toString();
        if (currentText.length() > 500) currentText = currentText.substring(250);
        rawLogsText.setText(currentText + "\n" + text);
        rawLogsScroll.post(() -> rawLogsScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    public void updateScale(Double cents) {
        if (tunerPointer == null) return;
        if (cents == null) {
            tunerPointer.setVisibility(View.INVISIBLE);
            addWaterfallLine(0, null);
            return;
        }

        double clampedCents = Math.max(-50, Math.min(50, cents));
        View parent = (View) tunerPointer.getParent();
        int width = parent.getWidth();
        if (width == 0) return;

        float x = (float) ((clampedCents / 100.0) * width);

        tunerPointer.setVisibility(View.VISIBLE);
        tunerPointer.animate().translationX(x).setDuration(50).start();
        addWaterfallLine(x, clampedCents);
    }

    private void addWaterfallLine(float translationX, Double cents) {
        if (waterfallContainer == null || !isAdded()) return;
        int lineHeight = 10;
        FrameLayout wrapper = new FrameLayout(requireContext());
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, lineHeight));

        if (cents != null) {
            View dot = new View(requireContext());
            int dotSize = 10;
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dotSize, dotSize);
            dotLp.gravity = Gravity.CENTER_VERTICAL;
            dot.setLayoutParams(dotLp);
            
            if (Math.abs(cents) < 2) {
                dot.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light));
            } else {
                dot.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.white));
            }

            dot.setTranslationX(waterfallContainer.getWidth() / 2f + translationX - (dotSize / 2f));
            wrapper.addView(dot);
        }

        waterfallContainer.addView(wrapper, 0);
        if (waterfallContainer.getChildCount() > 20) {
            waterfallContainer.removeViewAt(waterfallContainer.getChildCount() - 1);
        }

        for (int i = 0; i < waterfallContainer.getChildCount(); i++) {
            float alpha = 1.0f - (i / (float) waterfallContainer.getChildCount());
            waterfallContainer.getChildAt(i).setAlpha(alpha);
        }
    }
}
