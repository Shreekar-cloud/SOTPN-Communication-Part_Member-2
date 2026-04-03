package com.sotpn;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.sotpn.ui.SendActivity;
import com.sotpn.ui.ReceiveActivity;
import com.sotpn.ui.TransactionStatusActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnGoSend).setOnClickListener(v ->
                startActivity(new Intent(this, SendActivity.class)));

        findViewById(R.id.btnGoReceive).setOnClickListener(v ->
                startActivity(new Intent(this, ReceiveActivity.class)));

        findViewById(R.id.btnGoStatus).setOnClickListener(v ->
                startActivity(new Intent(this, TransactionStatusActivity.class)));
    }
}