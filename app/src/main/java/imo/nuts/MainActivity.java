package imo.nuts;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import io.github.rosemoe.editor.langs.EmptyLanguage;
import io.github.rosemoe.editor.langs.desc.CDescription;
import io.github.rosemoe.editor.langs.desc.CppDescription;
import io.github.rosemoe.editor.langs.desc.JavaScriptDescription;
import io.github.rosemoe.editor.langs.s5droid.S5droidAutoComplete;
import io.github.rosemoe.editor.langs.s5droid.S5droidLanguage;
import io.github.rosemoe.editor.langs.universal.UniversalLanguage;
import io.github.rosemoe.editor.struct.NavigationLabel;
import io.github.rosemoe.editor.utils.CrashHandler;

import java.util.List;

import io.github.rosemoe.editor.langs.java.JavaLanguage;
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
        if (getActionBar() != null) {
            Editable title = Editable.Factory.getInstance().newEditable("CodeEditor");
            title.setSpan(new ForegroundColorSpan(0xffffffff), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            getActionBar().setTitle(title);
        }

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SubMenu sub = menu.addSubMenu(0, 9999, 0, "Cursor Actions");
        //sub.add(0,10,0,"Go to line");
        sub.add(0, 2, 0, "Go To End");
        sub.add(0, 3, 0, "Move Up");
        sub.add(0, 4, 0, "Move Down");
        sub.add(0, 5, 0, "Home");
        sub.add(0, 6, 0, "End");
        sub.add(0, 7, 0, "Move Left");
        sub.add(0, 8, 0, "Move Right");
        sub = menu.addSubMenu(0, 99999, 0, "Text Actions");
        sub.add(0, 0, 0, "Undo");
        sub.add(0, 1, 0, "Redo");
        sub.add(0, 11, 0, "Copy");
        sub.add(0, 12, 0, "Paste");
        sub.add(0, 13, 0, "Cut");
        menu.add(0, 9, 0, "Code Navigation");
        menu.add(0, 10, 0, "Format");
        menu.add(0, 14, 0, "Switch language");
        menu.add(0, 15, 0, "Search");
        menu.add(0, 16, 0, "Search (Action Mode)");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //long st = System.nanoTime();
        try {
            switch (item.getItemId()) {
                case 0:
                    editor.undo();
                    break;
                case 1:
                    editor.redo();
                    break;
                case 2:
                    editor.setSelection(editor.getText().getLineCount() - 1, editor.getText().getColumnCount(editor.getText().getLineCount() - 1));
                    break;
                case 3:
                    editor.moveSelectionUp();
                    break;
                case 4:
                    editor.moveSelectionDown();
                    break;
                case 5:
                    editor.moveSelectionHome();
                    break;
                case 6:
                    editor.moveSelectionEnd();
                    break;
                case 7:
                    editor.moveSelectionLeft();
                    break;
                case 8:
                    editor.moveSelectionRight();
                    break;
                case 9: {
                    final List<NavigationLabel> labels = editor.getTextColor().getNavigation();
                    if (labels == null) {
                        Toast.makeText(this, "Code navigation not prepared or unsupported", Toast.LENGTH_SHORT).show();
                    } else {
                        CharSequence[] items = new CharSequence[labels.size()];
                        for (int i = 0; i < labels.size(); i++) {
                            items[i] = labels.get(i).label;
                        }
                        new AlertDialog.Builder(this)
                                .setTitle("Code navigation")
                                .setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface p1, int p2) {
                                        editor.jumpToLine(labels.get(p2).line);
                                        p1.dismiss();
                                    }

                                })
                                .setPositiveButton("Cancel", null)
                                .show();
                    }
                    break;
                }
                case 10:
                    editor.formatCodeAsync();
                    break;
                case 11:
                    editor.copyText();
                    break;
                case 12:
                    editor.pasteText();
                    break;
                case 13:
                    editor.cutText();
                    break;
                case 14:
                    new AlertDialog.Builder(this)
                            .setTitle("Switch language")
                            .setSingleChoiceItems(new String[]{"C", "C++", "Java", "JavaScript", "S5d", "None"}, 0, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    switch (which) {
                                        case 0:
                                            editor.setEditorLanguage(new UniversalLanguage(new CDescription()));
                                            break;
                                        case 1:
                                            editor.setEditorLanguage(new UniversalLanguage(new CppDescription()));
                                            break;
                                        case 2:
                                            editor.setEditorLanguage(new JavaLanguage());
                                            break;
                                        case 3:
                                            editor.setEditorLanguage(new UniversalLanguage(new JavaScriptDescription()));
                                            break;
                                        case 4:
                                            editor.setEditorLanguage(new S5droidLanguage());
                                            break;
                                        case 5:
                                            editor.setEditorLanguage(new EmptyLanguage());
                                    }
                                    dialog.dismiss();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    break;
                case 15:
                    if (panel.getVisibility() == View.GONE) {
                        replace.setText("");
                        search.setText("");
                        editor.getSearcher().stopSearch();
                        panel.setVisibility(View.VISIBLE);
                    } else {
                        panel.setVisibility(View.GONE);
                        editor.getSearcher().stopSearch();
                    }
                    break;
                case 16:
                    editor.beginSearchMode();
                    break;
            }
        } catch (Exception t) {
            StringBuilder sb = new StringBuilder();
            sb.append(t.toString());
            for (Object o : t.getStackTrace()) {
                sb.append('\n').append(o);
            }
            new AlertDialog.Builder(this)
                    .setTitle("Error occurred!")
                    .setMessage(sb)
                    .setPositiveButton("Cancel", null)
                    .show();
        }
        //Toast.makeText(this,"Action done in " + (System.nanoTime() - st) / 1e6 + " ms.",Toast.LENGTH_SHORT).show();

        return super.onOptionsItemSelected(item);
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
