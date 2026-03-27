package com.xrdxrf.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class XrdReferenceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xrd_reference);

        Button btnRetrieve = findViewById(R.id.btnRetrieve);
        btnRetrieve.setOnClickListener(v ->
                Toast.makeText(this, "Reference lookup not implemented yet", Toast.LENGTH_SHORT).show());
    }
}
