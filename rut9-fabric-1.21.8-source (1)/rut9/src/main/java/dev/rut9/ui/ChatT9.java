package dev.rut9.ui;

import dev.rut9.Rut9Client;
import dev.rut9.config.Rut9Config;
import dev.rut9.engine.T9Engine;
import dev.rut9.engine.Tx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Логика T9 в поле ввода чата: слежение за текстом, автозамена, отмена, выбор вариантов. */
public final class ChatT9 {
    private static final ChatT9 INSTANCE = new ChatT9();

    public static ChatT9 get() { return INSTANCE; }

    private record Undo(String original, String restored, String applied) {}

    private final SuggestionPanel panel = new SuggestionPanel();
    private String lastText = "";
    private Undo undo;

    private static boolean delimiter(char c) {
        return c == ' ' || c == ',' || c == '.' || c == '!' || c == '?' || c == ';' || c == ':' || c == ')';
    }

    public void onOpen(TextFieldWidget f) {
        lastText = f.getText();
        undo = null;
        Rut9Client c = Rut9Client.get();
        if (c != null) c.engine().requestSuggestions(lastText);
    }

    // ---------------------------------------------------------------- render / слежение за текстом

    public void onRender(TextFieldWidget f, DrawContext ctx, int mx, int my) {
        Rut9Client c = Rut9Client.get();
        if (c == null || !c.config().enabled) return;
        Rut9Config cfg = c.config();
        T9Engine eng = c.engine();

        String text = f.getText();
        if (!text.equals(lastText)) {
            String old = lastText;
            lastText = text;
            if (text.startsWith("/")) {
                undo = null;
                eng.clearSuggestions();
            } else {
                undo = null;
                if (!handleAutocorrect(f, old, text, cfg, eng)) eng.requestSuggestions(text);
            }
        }

        if (cfg.panelVisible && !f.getText().startsWith("/")) {
            MinecraftClient mc = MinecraftClient.getInstance();
            String status = eng.ready() ? null : "T9: загрузка словаря…";
            panel.render(ctx, mc.textRenderer, eng.suggestions(), mx, my, cfg.darkTheme, status,
                    mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        }
    }

    /** Автозамена слова при вводе пробела/знака препинания. @return true, если текст изменён. */
    private boolean handleAutocorrect(TextFieldWidget f, String old, String now, Rut9Config cfg, T9Engine eng) {
        if (!(cfg.autocorrect || cfg.autoCapitalize)) return false;
        if (now.length() != old.length() + 1 || !now.startsWith(old) || f.getCursor() != now.length()) return false;
        char d = now.charAt(now.length() - 1);
        if (!delimiter(d)) return false;

        String before = now.substring(0, now.length() - 1);
        String word = Tx.trailingWord(before);
        if (word.isEmpty()) return false;
        String head = before.substring(0, before.length() - word.length());

        String fixed = fixWord(word, head, cfg, eng);
        if (fixed == null || fixed.equals(word)) return false;

        String newText = head + fixed + d;
        apply(f, newText);
        undo = new Undo(word, head + word, newText);
        eng.requestSuggestions(newText);
        return true;
    }

    private String fixWord(String word, String head, Rut9Config cfg, T9Engine eng) {
        if (head.endsWith("@") || head.endsWith("#")) return null;
        String base = word;
        if (cfg.layoutFix && !Tx.isCyrillicWord(word)) {
            String lf = eng.fixLayout(word);
            if (lf != null) base = Tx.matchCase(word, lf);
        }
        if (!Tx.isCyrillicWord(base)) return null;

        String res = base;
        boolean start = Tx.sentenceStart(head);
        if (cfg.autocorrect) {
            List<String> ws = Tx.tokenize(Tx.sentenceOf(head));
            String prev = ws.isEmpty() ? "<s>" : ws.get(ws.size() - 1);
            String corr = eng.correct(base, prev, start);
            if (corr != null) res = corr;
        }
        if (cfg.autoCapitalize && start) res = Tx.capitalize(res);
        return res;
    }

    private void apply(TextFieldWidget f, String text) {
        f.setText(text);
        lastText = f.getText();
    }

    // ---------------------------------------------------------------- клавиатура

    public boolean onKey(TextFieldWidget f, int key, int mods) {
        Rut9Client c = Rut9Client.get();
        if (c == null) return false;
        Rut9Config cfg = c.config();
        T9Engine eng = c.engine();
        if (!cfg.enabled) return false;

        if (key == GLFW.GLFW_KEY_F8) {
            cfg.panelVisible = !cfg.panelVisible;
            cfg.save();
            return true;
        }

        String text = f.getText();

        // Backspace сразу после автозамены — вернуть исходное слово и запомнить его как «своё».
        if (key == GLFW.GLFW_KEY_BACKSPACE && undo != null && text.equals(undo.applied())) {
            eng.ignore(undo.original());
            apply(f, undo.restored());
            undo = null;
            eng.requestSuggestions(f.getText());
            return true;
        }

        if (text.startsWith("/")) return false;

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            finish(f, cfg, eng);
            return false; // отправку выполняет ванильный ChatScreen
        }
        if (key == GLFW.GLFW_KEY_TAB && cfg.tabAccepts && cfg.panelVisible && !eng.suggestions().isEmpty()) {
            pick(f, 0, eng);
            return true;
        }
        if ((mods & GLFW.GLFW_MOD_ALT) != 0 && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_3) {
            int i = key - GLFW.GLFW_KEY_1;
            if (i < eng.suggestions().size()) {
                pick(f, i, eng);
                return true;
            }
        }
        return false;
    }

    /** Перед отправкой: исправить последнее слово, расставить знаки, обучиться. */
    private void finish(TextFieldWidget f, Rut9Config cfg, T9Engine eng) {
        String t = f.getText();
        if (t.isBlank()) return;
        String out = t;
        String w = Tx.trailingWord(out);
        if (!w.isEmpty()) {
            String head = out.substring(0, out.length() - w.length());
            String fixed = fixWord(w, head, cfg, eng);
            if (fixed != null) out = head + fixed;
        }
        if (cfg.smartPunctuation) out = eng.punctuate(out);
        if (!out.equals(t)) apply(f, out);
        eng.learn(out);
    }

    private void pick(TextFieldWidget f, int i, T9Engine eng) {
        List<String> s = eng.suggestions();
        if (i < 0 || i >= s.size()) return;
        String sug = s.get(i);
        String text = f.getText();
        String partial = Tx.trailingWord(text);
        String head = text.substring(0, text.length() - partial.length());
        String newText = head + sug + " ";
        apply(f, newText);
        eng.recordPick(head, sug);
        undo = null;
        eng.requestSuggestions(f.getText());
    }

    // ---------------------------------------------------------------- мышь

    public boolean onMouse(TextFieldWidget f, double mx, double my, int button) {
        Rut9Client c = Rut9Client.get();
        if (c == null || button != 0) return false;
        Rut9Config cfg = c.config();
        if (!cfg.enabled || !cfg.panelVisible || f.getText().startsWith("/")) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        int i = panel.hit(mx, my, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
                c.engine().suggestions().size());
        if (i < 0) return false;
        pick(f, i, c.engine());
        return true;
    }
}
