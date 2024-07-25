/*
 *   Copyright 2020 Rosemoe
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package io.github.rosemoe.editor.langs.s5droid;

import android.content.Context;

import io.github.rosemoe.editor.interfaces.AutoCompleteProvider;
import io.github.rosemoe.editor.struct.ResultItem;
import io.github.rosemoe.editor.langs.internal.Pinyin;
import io.github.rosemoe.editor.struct.NavigationLabel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.rosemoe.editor.text.TextAnalyzeResult;

/**
 * @author Rose
 */
public class S5droidAutoComplete implements AutoCompleteProvider {

    private static final HashMap<String, String[]> sMethods;
    private static final HashMap<String, String[]> sEvents;
    private HashMap<String, String> mComponents;
    private List<String> mComponentNames;
    private static final List<String> sClasses;

    static {
        sMethods = new HashMap<>();
        sEvents = new HashMap<>();
        sClasses = new ArrayList<>();
    }

    public static void init(Context context) {
        addMethodAndEvent(context, "contents/functions.txt");
        addMethodAndEvent(context, "contents/event.txt");
    }

    public static String readAssets(Context context, String filename) {
        byte[] buffer;
        try {
            InputStream inputstream = context.getAssets().open(filename);
            buffer = new byte[inputstream.available()];
            int count = inputstream.read(buffer);
            if (count != buffer.length) {
                System.err.println("Assets file read incompletely:" + filename);
            }
            inputstream.close();
            return new String(buffer);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static void addMethodAndEvent(Context context, String assetsName) {
        List<String> list1 = new ArrayList<>();
        List<String> list2 = new ArrayList<>();
        try {
            String text = readAssets(context, assetsName);
            Pattern p1 = Pattern.compile("】(.|[\r\n])*?【");
            Matcher m1 = p1.matcher(text);
            while (m1.find()) {
                list1.add(m1.group().replace("@【", "").replace("】@", ""));
            }
            Pattern p2 = Pattern.compile("【(.*?)】");
            Matcher m2 = p2.matcher(text);
            while (m2.find()) {
                list2.add(m2.group().replace("【", "").replace("】", ""));
            }
            for (int i = 0; i < list2.size(); i++) {
                String[] strs = list1.get(i).split("@");
                if (assetsName.contains("function")) {
                    addPackage(list2.get(i), strs);
                } else {
                    addEvent(list2.get(i), strs);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Tip for text after '.'
     *
     * @param pkg The simplified class name
     */
    public static void addPackage(String pkg, String[] methods) {
        sMethods.put(pkg, methods);
        sClasses.add(pkg);
    }

    /**
     * Tip for text after ':'
     *
     * @param component The simplified class name of components
     */
    public static void addEvent(String component, String[] events) {
        sEvents.put(component, events);
    }

    public S5droidAutoComplete() {
        mComponents = new HashMap<>();
        mComponentNames = new ArrayList<>();
    }

    /**
     * Add a variant for layout component
     */
    public void addComponent(String componentName, String componentType) {
        mComponents.put(componentName.toLowerCase(), componentType);
        mComponentNames.add(componentName.toLowerCase());
    }

    /**
     * Clear layout components
     */
    public void clearComponents() {
        mComponents.clear();
        mComponentNames.clear();
    }

    public String getComponentVariantType(String name) {
        return mComponents.get(name);
    }

    public String[] getComponentEvents(String name) {
        return sEvents.get(name);
    }

    public String[] getComponentMethods(String name) {
        return sMethods.get(name);
    }

    private final static String[] keywordsInner = {
            "返回", "结束",
            "循环", "判断", "分支",
            "如果", "则", "否则", "否则如果", "步进", "步退",
            "容错处理", "容错", "捕获",
            "变量循环", "判断循环", "至",
            "断言", "跳出", "跳过", "创建", "与", "或",
            "事件", "方法", "从属于", "真", "假",
            "空", "本对象", "变量", "为", "文本型", "整数型",
            "逻辑型", "浮点数型", "双精度型", "长整数型",
            "对象"
    };

    private final static String[] keywordsOutside = new String[]{
            "变量", "为", "文本型", "整数型",
            "逻辑型", "浮点数型", "双精度型", "长整数型",
            "对象", "静态", "创建", "与", "或",
            "事件", "方法", "从属于", "真", "假",
            "空", "本对象",
    };

    @Override
    public List<ResultItem> getAutoCompleteItems(String prefix, boolean isInCodeBlock, TextAnalyzeResult colors, int line) {
        List<NavigationLabel> mCustomMethods = colors.getNavigation();
        S5dTextTokenizer tk = new S5dTextTokenizer("");
        String lowCase = prefix.toLowerCase();
        List<ResultItem> kws = new ArrayList<>();
        List<ResultItem> methods = new ArrayList<>();
        List<ResultItem> classes = new ArrayList<>();
        List<ResultItem> fields = new ArrayList<>();
        if (lowCase.contains(":")) {
            String[] split = lowCase.split(":");
            if (split.length == 2) {
                String componentType = getComponentVariantType(split[0]);
                String[] componentEvents = getComponentEvents(componentType);
                if (componentEvents != null) {
                    for (String event : componentEvents) {
                        int p = event.indexOf("(");
                        String eventName = event.substring(0, p);
                        if (eventName.startsWith(split[1])) {
                            kws.add(new ResultItem(eventName + "()", split[0] + ":" + eventName + "()", event, ResultItem.TYPE_LOCAL_METHOD));
                        }
                    }
                    Collections.sort(kws, ResultItem.COMPARATOR_BY_NAME);
                }
            }
            return kws;
        }
        String[] split = lowCase.split("\\.");
        if (lowCase.endsWith(".")) {
            split = new String[]{split[0], ""};
        }
        if (split.length == 1) {
            lowCase = split[0];
            String[] keywords = isInCodeBlock ? keywordsInner : keywordsOutside;
            for (String keyword : keywords) {
                if (whetherAdd(lowCase, keyword.toLowerCase())) {
                    kws.add(new ResultItem(keyword, "结绳关键字"));
                }
            }

            S5droidTree tree = (S5droidTree) colors.mExtra;
            findForPrefix(split[0], tree.root, tree.root, line, fields);

            for (String s : sClasses) {
                if (whetherAdd(split[0], s) && !s.equals("文本型")) {
                    classes.add(new ResultItem(s, "结绳核心类库"));
                }
            }

            for (String s : mComponentNames) {
                if (whetherAdd(split[0], s) && !s.equals("文本型")) {
                    classes.add(new ResultItem(s, "布局设计变量 - " + getComponentVariantType(s)));
                }
            }

            if (mCustomMethods != null) {
                int count = mCustomMethods.size() - 1;
                for (int i = count; i >= 0; i--) {
                    NavigationLabel navi = mCustomMethods.get(i);
                    tk.reset(navi.label);
                    tk.setSkipWhitespace(true);
                    if (tk.nextToken() == Tokens.METHOD) {
                        if (tk.nextToken() != Tokens.IDENTIFIER) {
                            continue;
                        }
                        String name = (String) tk.getTokenString();
                        if (whetherAdd(lowCase, name.toLowerCase())) {
                            methods.add(new ResultItem(name + "()", navi.label + "", ResultItem.TYPE_LOCAL_METHOD).mask(ResultItem.MASK_SHIFT_LEFT_TWICE));
                        }
                    } else {
                        break;
                    }
                }
            }

            String[] funcs = getComponentMethods("窗口");
            for (String func : funcs) {
                int p = func.indexOf("(");
                String funcName = func.substring(0, p);
                if (whetherAdd(split[0], funcName)) {
                    int q = func.lastIndexOf(")");
                    int mask = 0;
                    String suffix = "()";
                    boolean hasResult = q != func.length() - 1;
                    boolean hasArg = p != q - 1;
                    if (hasResult) {
                        if (hasArg) {
                            mask |= ResultItem.MASK_SHIFT_LEFT_ONCE;
                        }
                    } else {
                        suffix += ";";
                        if (hasArg) {
                            mask |= ResultItem.MASK_SHIFT_LEFT_TWICE;
                        }
                    }
                    methods.add(new ResultItem(funcName + "()", funcName + suffix, func, ResultItem.TYPE_LOCAL_METHOD).mask(mask));
                }
            }

        } else if (split.length == 2) {
            String componentName = split[0];
            String componentType = getComponentVariantType(componentName);
            String[] funcs = getComponentMethods(componentType);
            if (funcs != null) {
                for (String func : funcs) {
                    int p = func.indexOf("(");
                    String funcName = func.substring(0, p);
                    if (whetherAdd(split[1], funcName)) {
                        int q = func.lastIndexOf(")");
                        int mask = 0;
                        String suffix = "()";
                        boolean hasResult = q != func.length() - 1;
                        boolean hasArg = p != q - 1;
                        if (hasResult) {
                            if (hasArg) {
                                mask |= ResultItem.MASK_SHIFT_LEFT_ONCE;
                            }
                        } else {
                            suffix += ";";
                            if (hasArg) {
                                mask |= ResultItem.MASK_SHIFT_LEFT_TWICE;
                            }
                        }
                        methods.add(new ResultItem(funcName + "()", split[0] + "." + funcName + suffix, func, ResultItem.TYPE_LOCAL_METHOD).mask(mask));
                    }
                }
            } else {
                S5droidTree tree = (S5droidTree) colors.mExtra;
                String[] result = find(split[0], tree.root, tree.root, line);
                componentType = result[1];
                split[0] = result[0];
                if (componentType != null) {
                    funcs = getComponentMethods(componentType);
                    if (funcs != null) {
                        for (String func : funcs) {
                            int p = func.indexOf("(");
                            if (p != -1) {
                                String funcName = func.substring(0, p);
                                if (whetherAdd(split[1], funcName)) {
                                    int q = func.lastIndexOf(")");
                                    int mask = 0;
                                    String suffix = "()";
                                    boolean hasResult = q != func.length() - 1;
                                    boolean hasArg = p != q - 1;
                                    if (hasResult) {
                                        if (hasArg) {
                                            mask |= ResultItem.MASK_SHIFT_LEFT_ONCE;
                                        }
                                    } else {
                                        suffix += ";";
                                        if (hasArg) {
                                            mask |= ResultItem.MASK_SHIFT_LEFT_TWICE;
                                        }
                                    }
                                    methods.add(new ResultItem(funcName + "()", split[0] + "." + funcName + suffix, func, ResultItem.TYPE_LOCAL_METHOD).mask(mask));
                                }
                            }
                        }
                    }
                }
                funcs = getComponentMethods(split[0]);
                if (funcs != null) {
                    for (String func : funcs) {
                        int p = func.indexOf("(");
                        if (p == -1) {
                            continue;
                        }
                        String funcName = func.substring(0, p);
                        if (whetherAdd(split[1], funcName)) {
                            int q = func.lastIndexOf(")");
                            int mask = 0;
                            String suffix = "()";
                            boolean hasResult = q != func.length() - 1;
                            boolean hasArg = p != q - 1;
                            if (hasResult) {
                                if (hasArg) {
                                    mask |= ResultItem.MASK_SHIFT_LEFT_ONCE;
                                }
                            } else {
                                suffix += ";";
                                if (hasArg) {
                                    mask |= ResultItem.MASK_SHIFT_LEFT_TWICE;
                                }
                            }
                            methods.add(new ResultItem(funcName + "()", split[0] + "." + funcName + suffix, func, ResultItem.TYPE_LOCAL_METHOD).mask(mask));
                        }
                    }
                }
            }
        }
        Collections.sort(kws, ResultItem.COMPARATOR_BY_NAME);
        Collections.sort(classes, ResultItem.COMPARATOR_BY_NAME);
        Collections.sort(fields, ResultItem.COMPARATOR_BY_NAME);
        Collections.sort(methods, ResultItem.COMPARATOR_BY_NAME);
        kws.addAll(fields);
        kws.addAll(classes);
        kws.addAll(methods);
        return kws;
    }

    /**
     * Whether this item matches prefix
     *
     * @param a User's input
     * @param b This item
     * @return Whether should be added
     */
    private static boolean whetherAdd(String a, String b) {
        b = b.toLowerCase();
        a = a.toLowerCase();
        return (b.toLowerCase().contains(a) || Pinyin.getFirstPinyinForWords(b).contains(a) || Pinyin.getPinyinForWords(b).contains(a));
    }

    private void findForPrefix(String match, S5droidTree.Node node, final S5droidTree.Node root, int line, List<ResultItem> tips) {
        if (!node.isBlock) {
            if (whetherAdd(match, node.varName.toLowerCase()) && (node.parent == root || node.startLine < line)) {
                tips.add(new ResultItem(node.varName, "变量 - " + node.varType));
            }
            return;
        }
        if (node.startLine > line || node.endLine < line) {
            return;
        }
        for (int i = 0; i < node.children.size(); i++) {
            S5droidTree.Node sub = node.children.get(i);
            findForPrefix(match, sub, root, line, tips);
        }
    }

    private final static String[] NO_RESULT = new String[]{null, null};

    private String[] find(String match, S5droidTree.Node node, S5droidTree.Node root, int line) {
        for (int i = 0; i < node.children.size(); i++) {
            S5droidTree.Node sub = node.children.get(i);
            if (sub.isBlock) {
                if (sub.startLine <= line && sub.endLine >= line) {
                    String[] res = find(match, sub, root, line);
                    if (res != NO_RESULT) {
                        return res;
                    }
                }
            } else {
                if (node == root || (sub.startLine < line)) {
                    if (sub.varName.toLowerCase().equals(match)) {
                        return new String[]{sub.varName, sub.varType};
                    }
                }
            }
        }
        return NO_RESULT;
    }
}

