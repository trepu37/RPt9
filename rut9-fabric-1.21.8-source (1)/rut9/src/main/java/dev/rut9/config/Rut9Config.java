package dev.rut9.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки мода. Файл: config/rut9/config.json. Меняются командой /t9. */
public final class Rut9Config {
    public boolean enabled = true;
    public boolean autocorrect = true;
    public boolean predictions = true;
    public boolean learning = true;
    public boolean panelVisible = true;
    public boolean darkTheme = true;
    public boolean layoutFix = true;        // «ghbdtn» -> «привет»
    public boolean autoCapitalize = true;   // заглавная в начале предложения
    public boolean smartPunctuation = true; // запятые/«?» при отправке
    public boolean addPeriod = false;       // точка в конце длинных фраз
    public boolean tabAccepts = true;       // Tab принимает первый вариант

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("rut9");
    }

    public static Rut9Config load() {
        Path p = dir().resolve("config.json");
        try {
            if (Files.exists(p)) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    Rut9Config c = GSON.fromJson(r, Rut9Config.class);
                    if (c != null) return c;
                }
            }
        } catch (Exception ignored) {
        }
        Rut9Config c = new Rut9Config();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("config.json"), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
