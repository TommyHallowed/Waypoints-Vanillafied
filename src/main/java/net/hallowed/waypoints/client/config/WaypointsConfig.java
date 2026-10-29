package net.hallowed.waypoints.client.config;

import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class WaypointsConfig {

    public static final ConfigClassHandler<WaypointsConfig> HANDLER = ConfigClassHandler.createBuilder(WaypointsConfig.class)
            .id(Identifier.fromNamespaceAndPath("waypoints_vanillafied", "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("waypoints_vanillafied.json5"))
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting)
                    .setJson5(true)
                    .build())
            .build();

    @SerialEntry(comment = "Hides the vanilla locator bar background and makes the Experience bar take priority over it, so waypoint icons render on top of the XP bar instead.")
    public boolean hideLocatorBackground = true;

    @SerialEntry(comment = "Whether death points are automatically created when you die.")
    public boolean createDeathPoints = true;

    @SerialEntry(comment = "Minutes after which death points are automatically deleted. Set to 0 to never delete them.")
    public int deathPointExpiryMinutes = 60;

    @SerialEntry(comment = "Controls when the waypoint name/distance tooltip is shown above the locator bar.")
    public TooltipMode tooltipMode = TooltipMode.TAB;

    @SerialEntry(comment = "Whether up/down pitch arrows are rendered next to waypoint icons.")
    public boolean showPitchArrows = true;

    public enum TooltipMode implements NameableEnum {
        ALWAYS,
        HIDDEN,
        TAB;

        @Override
        public Component getDisplayName() {
            return Component.translatable("waypoints_vanillafied.config.tooltip_mode." + name().toLowerCase());
        }
    }

    public static Screen createScreen(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("waypoints_vanillafied.config.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("waypoints_vanillafied.config.category.general"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("waypoints_vanillafied.config.hide_locator_background"))
                                .description(OptionDescription.of(Component.translatable("waypoints_vanillafied.config.hide_locator_background.desc")))
                                .binding(true,
                                        () -> HANDLER.instance().hideLocatorBackground,
                                        val -> HANDLER.instance().hideLocatorBackground = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("waypoints_vanillafied.config.create_death_points"))
                                .description(OptionDescription.of(Component.translatable("waypoints_vanillafied.config.create_death_points.desc")))
                                .binding(true,
                                        () -> HANDLER.instance().createDeathPoints,
                                        val -> HANDLER.instance().createDeathPoints = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("waypoints_vanillafied.config.death_point_expiry_minutes"))
                                .description(OptionDescription.of(Component.translatable("waypoints_vanillafied.config.death_point_expiry_minutes.desc")))
                                .binding(60,
                                        () -> HANDLER.instance().deathPointExpiryMinutes,
                                        val -> HANDLER.instance().deathPointExpiryMinutes = val)
                                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, 10080))
                                .build())
                        .option(Option.<TooltipMode>createBuilder()
                                .name(Component.translatable("waypoints_vanillafied.config.tooltip_mode"))
                                .description(OptionDescription.of(Component.translatable("waypoints_vanillafied.config.tooltip_mode.desc")))
                                .binding(TooltipMode.TAB,
                                        () -> HANDLER.instance().tooltipMode,
                                        val -> HANDLER.instance().tooltipMode = val)
                                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(TooltipMode.class))
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("waypoints_vanillafied.config.show_pitch_arrows"))
                                .description(OptionDescription.of(Component.translatable("waypoints_vanillafied.config.show_pitch_arrows.desc")))
                                .binding(true,
                                        () -> HANDLER.instance().showPitchArrows,
                                        val -> HANDLER.instance().showPitchArrows = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())
                .save(HANDLER::save)
                .build()
                .generateScreen(parent);
    }
}