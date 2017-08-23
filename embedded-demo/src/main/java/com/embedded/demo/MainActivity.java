package com.embedded.demo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.termux.embedded.TerminalHelper;

public class MainActivity extends AppCompatActivity {

    private TerminalHelper mTerminalHelper;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final View contentView = findViewById(R.id.content);
        findViewById(R.id.btn_action).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mTerminalHelper == null){
                    mTerminalHelper = new TerminalHelper(MainActivity.this, "main");
                }else{
                    mTerminalHelper.onVisible();
                    mTerminalHelper.replaceView(contentView);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mTerminalHelper != null) mTerminalHelper.onDestroy(this);
    }
}
