package imo.nuts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

public class debug extends Activity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		Intent intent = getIntent();
        
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(intent.getStringExtra("error"));
		builder.show();
    }
}
