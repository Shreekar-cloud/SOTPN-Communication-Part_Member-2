package com.sotpn.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.sotpn.R;
import com.sotpn.model.TransactionPhase;
import com.sotpn.transaction.AdaptiveDelayCalculator;

/**
 * SOTPN - Secure Offline Token-Based Payment Network
 * Member 2: Communication + Transaction Engine
 *
 * File     : TransactionStatusActivity.java
 * Package  : com.sotpn.ui
 * Step     : Step 9 - ViewModel + UI
 * Status   : Complete
 */
public class TransactionStatusActivity extends AppCompatActivity {

    private static final String ICON_PENDING  = "○";
    private static final String ICON_ACTIVE   = "◉";
    private static final String ICON_DONE     = "✅";
    private static final String ICON_FAILED   = "❌";

    private static final int COLOR_PENDING  = Color.parseColor("#CCCCCC");
    private static final int COLOR_ACTIVE   = Color.parseColor("#2196F3");
    private static final int COLOR_DONE     = Color.parseColor("#4CAF50");
    private static final int COLOR_FAILED   = Color.parseColor("#F44336");

    private TextView iconPhase1, iconPhase2, iconPhase3, iconPhase4;
    private View        cardDelay;
    private TextView    tvCountdown;
    private ProgressBar delayProgressBar;
    private TextView    tvRiskLevel;
    private TextView tvStatusMessage;
    private Button   btnCancelStatus;

    private MainViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);

        bindViews();
        setupViewModel();
        setupCancelButton();
    }

    private void bindViews() {
        iconPhase1       = findViewById(R.id.iconPhase1);
        iconPhase2       = findViewById(R.id.iconPhase2);
        iconPhase3       = findViewById(R.id.iconPhase3);
        iconPhase4       = findViewById(R.id.iconPhase4);
        cardDelay        = findViewById(R.id.cardDelay);
        tvCountdown      = findViewById(R.id.tvCountdown);
        delayProgressBar = findViewById(R.id.delayProgressBar);
        tvRiskLevel      = findViewById(R.id.tvRiskLevel);
        tvStatusMessage  = findViewById(R.id.tvStatusMessage);
        btnCancelStatus  = findViewById(R.id.btnCancelStatus);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        viewModel.getCurrentPhase().observe(this, this::updatePhaseIcons);
        viewModel.getStatusMessage().observe(this, msg -> tvStatusMessage.setText(msg));

        viewModel.getDelayRemainingMs().observe(this, remaining -> {
            Long total = viewModel.getDelayTotalMs().getValue();
            if (total == null || total == 0) return;

            long secs = (remaining + 999) / 1000;
            tvCountdown.setText(secs + "s");

            int progress = (int) ((remaining * 100) / total);
            delayProgressBar.setProgress(progress);
        });

        viewModel.getRiskLevel().observe(this, risk ->
                tvRiskLevel.setText(describeRisk(risk)));

        viewModel.getCompletedProof().observe(this, proof -> {
            if (proof != null) {
                showSuccess(proof.getRole().name());
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                showFailure(error);
            }
        });

        viewModel.getConflictResult().observe(this, conflict -> {
            if (conflict != null && conflict.isConflict) {
                Toast.makeText(this,
                        "⚠️ Double-spend conflict detected! Transaction aborted.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupCancelButton() {
        btnCancelStatus.setOnClickListener(v -> {
            viewModel.cancelTransaction();
            finish();
        });
    }

    private void updatePhaseIcons(TransactionPhase phase) {
        if (phase == null) return;

        setIcon(iconPhase1, ICON_PENDING, COLOR_PENDING);
        setIcon(iconPhase2, ICON_PENDING, COLOR_PENDING);
        setIcon(iconPhase3, ICON_PENDING, COLOR_PENDING);
        setIcon(iconPhase4, ICON_PENDING, COLOR_PENDING);
        cardDelay.setVisibility(View.GONE);

        switch (phase) {
            case PREPARE:
                setIcon(iconPhase1, ICON_ACTIVE, COLOR_ACTIVE);
                break;

            case VALIDATING:
                setIcon(iconPhase1, ICON_DONE,   COLOR_DONE);
                setIcon(iconPhase2, ICON_ACTIVE,  COLOR_ACTIVE);
                break;

            case DELAYING:
                setIcon(iconPhase1, ICON_DONE,   COLOR_DONE);
                setIcon(iconPhase2, ICON_DONE,   COLOR_DONE);
                setIcon(iconPhase3, ICON_ACTIVE,  COLOR_ACTIVE);
                cardDelay.setVisibility(View.VISIBLE);
                break;

            case COMMITTING:
                setIcon(iconPhase1, ICON_DONE,   COLOR_DONE);
                setIcon(iconPhase2, ICON_DONE,   COLOR_DONE);
                setIcon(iconPhase3, ICON_DONE,   COLOR_DONE);
                setIcon(iconPhase4, ICON_ACTIVE,  COLOR_ACTIVE);
                break;


            case FINALIZED:
                setIcon(iconPhase1, ICON_DONE, COLOR_DONE);
                setIcon(iconPhase2, ICON_DONE, COLOR_DONE);
                setIcon(iconPhase3, ICON_DONE, COLOR_DONE);
                setIcon(iconPhase4, ICON_DONE, COLOR_DONE);
                btnCancelStatus.setText("Done");
                btnCancelStatus.setBackgroundColor(COLOR_DONE);
                break;

            case FAILED:
                setIcon(iconPhase1, ICON_FAILED, COLOR_FAILED);
                btnCancelStatus.setText("Close");
                break;
        }
    }

    private void showSuccess(String role) {
        String msg = role.equals("SENDER")
                ? "Payment sent successfully! ✅"
                : "Payment received successfully! ✅";
        tvStatusMessage.setText(msg);
        tvStatusMessage.setTextColor(COLOR_DONE);
        btnCancelStatus.setText("Done");
        btnCancelStatus.setBackgroundColor(COLOR_DONE);
        btnCancelStatus.setOnClickListener(v -> finish());
    }

    private void showFailure(String reason) {
        tvStatusMessage.setText("Failed: " + reason);
        tvStatusMessage.setTextColor(COLOR_FAILED);
        btnCancelStatus.setText("Close");
        btnCancelStatus.setOnClickListener(v -> finish());
    }

    private void setIcon(TextView view, String icon, int color) {
        if (view != null) {
            view.setText(icon);
            view.setTextColor(color);
        }
    }

    private String describeRisk(AdaptiveDelayCalculator.RiskLevel risk) {
        if (risk == null) return "";
        switch (risk) {
            case LOW:    return "Low risk — many peers nearby";
            case MEDIUM: return "Medium risk — standard verification";
            case HIGH:   return "High risk — few peers nearby";
            default:     return "";
        }
    }
}
