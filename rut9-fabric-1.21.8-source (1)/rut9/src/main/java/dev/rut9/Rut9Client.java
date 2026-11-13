package dev.rut9;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.rut9.config.Rut9Config;
import dev.rut9.engine.T9Engine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Rut9Client implements ClientModInitializer {
    private static Rut9Client instance;

    private Rut9Config config;
    private T9Engine engine;

    public static Rut9Client get() { return instance; }

    public Rut9Config config() { return config; }

    public T9Engine engine() { return engine; }

    @Override
    public void onInitializeClient() {
        instance = this;
        config = Rut9Config.load();
        engine = new T9Engine(config);
        engine.initAsync();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> engine.shutdown());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("t9")
                        .then(toggle("enabled", () -> config.enabled, v -> config.enabled = v))
                        .then(toggle("panel", () -> config.panelVisible, v -> config.panelVisible = v))
                        .then(toggle("autocorrect", () -> config.autocorrect, v -> config.autocorrect = v))
                        .then(toggle("predict", () -> config.predictions, v -> config.predictions = v))
                        .then(toggle("learn", () -> config.learning, v -> config.learning = v))
                        .then(toggle("punctuation", () -> config.smartPunctuation, v -> config.smartPunctuation = v))
                        .then(toggle("period", () -> config.addPeriod, v -> config.addPeriod = v))
                        .then(toggle("capitalize", () -> config.autoCapitalize, v -> config.autoCapitalize = v))
                        .then(toggle("layout", () -> config.layoutFix, v -> config.layoutFix = v))
                        .then(toggle("dark", () -> config.darkTheme, v -> config.darkTheme = v))
                        .then(toggle("tab", () -> config.tabAccepts, v -> config.tabAccepts = v))
                        .then(ClientCommandManager.literal("reset").executes(ctx -> {
                            engine.resetUser();
                            ctx.getSource().sendFeedback(Text.literal("T9: личный словарь очищен"));
                            return 1;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal(
                                    "T9: /t9 <enabled|panel|autocorrect|predict|learn|punctuation|period|capitalize|layout|dark|tab|reset>. F8 — скрыть/показать панель."));
                            return 1;
                        })));
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        return ClientCommandManager.literal(name).executes(ctx -> {
            boolean nv = !get.get();
            set.accept(nv);
            config.save();
            ctx.getSource().sendFeedback(Text.literal("T9 " + name + ": " + (nv ? "вкл" : "выкл")));
            return 1;
        });
    }
}
