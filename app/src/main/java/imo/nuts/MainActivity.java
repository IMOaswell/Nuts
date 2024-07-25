package imo.nuts;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import io.github.rosemoe.editor.langs.java.JavaLanguage;
import io.github.rosemoe.editor.langs.s5droid.S5droidAutoComplete;
import io.github.rosemoe.editor.utils.CrashHandler;
import io.github.rosemoe.editor.widget.CodeEditor;

public class MainActivity extends Activity {

    private CodeEditor editor;
    private LinearLayout panel;
    private EditText search, replace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.getInstance().init(this);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("We need storage access to collect crash logs.\nCrash logs will be placed under /sdcard/#Logs/\nIf you do not want to save crash logs, feel free to deny the permission request.\nClick 'Permit' to start the request.")
                    .setPositiveButton("Permit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            MainActivity.this.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 9998);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .setCancelable(false)
                    .show();

        }
        S5droidAutoComplete.init(this);
        editor = findViewById(R.id.editor);
        panel = findViewById(R.id.search_panel);
        search = findViewById(R.id.search_editor);
        replace = findViewById(R.id.replace_editor);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                editor.getSearcher().search(s.toString());
            }
        });

        editor.setEditorLanguage(new JavaLanguage());
        editor.setText("public class Main {\n\n\tpublic static void main(String[] args) {\n\t\t\n\t}\n\n}");
    }

    public void gotoNext(View view) {
        try {
            editor.getSearcher().gotoNext();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void gotoLast(View view) {
        try {
            editor.getSearcher().gotoLast();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void replace(View view) {
        try {
            editor.getSearcher().replaceThis(replace.getText().toString());
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void replaceAll(View view) {
        try {
            editor.getSearcher().replaceAll(replace.getText().toString());
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }
}
