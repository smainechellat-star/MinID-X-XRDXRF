package com.xrdxrf.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class XrdSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xrd_selection);

        View btnXrdMIdentification = findViewById(R.id.btnXrdMIdentification);
        View btnXrdDIdentification = findViewById(R.id.btnXrdDIdentification);
        View btnXrdOnePeakIdentification = findViewById(R.id.btnXrdOnePeakIdentification);

        btnXrdMIdentification.setOnClickListener(v ->
                startActivity(new Intent(this, XrdMIdentificationActivity.class)));

        btnXrdDIdentification.setOnClickListener(v ->
                startActivity(new Intent(this, XrdDIdentificationActivity.class)));

        btnXrdOnePeakIdentification.setOnClickListener(v ->
                startActivity(new Intent(this, XrdOnePeakIdentificationActivity.class)));
    }
}
