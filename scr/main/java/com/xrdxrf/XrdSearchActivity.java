package com.xrdxrf.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class XrdSearchActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xrd_search);

        Button btnSearch = findViewById(R.id.btnSearch);
        btnSearch.setOnClickListener(v ->
                Toast.makeText(this, "Search not implemented yet", Toast.LENGTH_SHORT).show());
    }
}
