package com.xrdxrf.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Button btnReadMe = findViewById(R.id.btnReadMe);
        Button btnAbout = findViewById(R.id.btnAbout);
        Button btnContactUs = findViewById(R.id.btnContactUs);

        btnReadMe.setOnClickListener(v ->
                startActivity(new Intent(this, ReadMeActivity.class)));

        btnAbout.setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        btnContactUs.setOnClickListener(v ->
                startActivity(new Intent(this, ContactActivity.class)));
    }
}
