/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.RelativeFile;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.helpers.Paginator;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.utils.SettingsUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;
import static baritone.api.utils.SettingsUtil.*;

public class SetCommand extends Command {

    public SetCommand(IBaritone baritone) {
        super(baritone, "set", "setting");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String arg = args.hasAny() ? args.getString().toLowerCase(Locale.US) : "list";
        if (Objects.equals("save", arg)) {
            SettingsUtil.save(Baritone.settings());
            logDirect("已保存的设置");
            return;
        }
        if (Objects.equals("load", arg)) {
            String file = SETTINGS_DEFAULT_NAME;
            if (args.hasAny()) {
                file = args.getString();
            }
            // reset to defaults
            SettingsUtil.modifiedSettings(Baritone.settings()).forEach(Settings.Setting::reset);
            // then load from disk
            SettingsUtil.readAndApply(Baritone.settings(), file);
            logDirect("重载设置, 来自 " + file);
            return;
        }
        boolean viewModified = Objects.equals("modified", arg);
        boolean viewAll = Objects.equals("list", arg);
        boolean paginate = viewModified || viewAll;
        if (paginate) {
            String search = args.hasAny() && args.peekAsOrNull(Integer.class) == null ? args.getString() : "";
            args.requireMax(1);
            List<? extends Settings.Setting> toPaginate =
                    (viewModified ? SettingsUtil.modifiedSettings(Baritone.settings()) : Baritone.settings().allSettings).stream()
                            .filter(s -> !s.isJavaOnly())
                            .filter(s -> s.getName().toLowerCase(Locale.US).contains(search.toLowerCase(Locale.US)))
                            .sorted((s1, s2) -> String.CASE_INSENSITIVE_ORDER.compare(s1.getName(), s2.getName()))
                            .collect(Collectors.toList());
            Paginator.paginate(
                    args,
                    new Paginator<>(toPaginate),
                    () -> logDirect(
                            !search.isEmpty()
                                    ? String.format("所有 %ssettings 包含字符串 '%s':", viewModified ? "已修改 " : "", search)
                                    : String.format("所有 %ssettings:", viewModified ? "已修改 " : "")
                    ),
                    setting -> {
                        MutableComponent typeComponent = Component.literal(String.format(
                                " (%s)",
                                settingTypeToString(setting)
                        ));
                        typeComponent.setStyle(typeComponent.getStyle().withColor(ChatFormatting.DARK_GRAY));
                        MutableComponent hoverComponent = Component.literal("");
                        hoverComponent.setStyle(hoverComponent.getStyle().withColor(ChatFormatting.GRAY));
                        hoverComponent.append(setting.getName());
                        hoverComponent.append(String.format("\n类型: %s", settingTypeToString(setting)));
                        hoverComponent.append(String.format("\n\n值:\n%s", settingValueToString(setting)));
                        hoverComponent.append(String.format("\n\n默认值:\n%s", settingDefaultToString(setting)));
                        String commandSuggestion = Baritone.settings().prefix.value + String.format("set %s ", setting.getName());
                        MutableComponent component = Component.literal(setting.getName());
                        component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));
                        component.append(typeComponent);
                        component.setStyle(component.getStyle()
                                .withHoverEvent(new HoverEvent.ShowText(hoverComponent))
                                .withClickEvent(new ClickEvent.SuggestCommand(commandSuggestion)));
                        return component;
                    },
                    FORCE_COMMAND_PREFIX + "set " + arg + " " + search
            );
            return;
        }
        args.requireMax(1);
        boolean resetting = arg.equalsIgnoreCase("reset");
        boolean toggling = arg.equalsIgnoreCase("toggle");
        boolean doingSomething = resetting || toggling;
        if (resetting) {
            if (!args.hasAny()) {
                logDirect("请将参数指定为'all'以重置, 以确认您确实想执行此操作");
                logDirect("所有设置将被重置. 使用'set modified'或'modified'命令查看将被重置的内容");
                logDirect("指定一个设置名称. 而不是'all', 以只重置一个设置");
            } else if (args.peekString().equalsIgnoreCase("all")) {
                SettingsUtil.modifiedSettings(Baritone.settings()).forEach(Settings.Setting::reset);
                logDirect("所有设置已重置为默认值");
                SettingsUtil.save(Baritone.settings());
                return;
            }
        }
        if (toggling) {
            args.requireMin(1);
        }
        String settingName = doingSomething ? args.getString() : arg;
        Settings.Setting<?> setting = Baritone.settings().allSettings.stream()
                .filter(s -> s.getName().equalsIgnoreCase(settingName))
                .findFirst()
                .orElse(null);
        if (setting == null) {
            throw new CommandInvalidTypeException(args.consumed(), "a valid setting");
        }
        if (setting.isJavaOnly()) {
            // ideally it would act as if the setting didn't exist
            // but users will see it in Settings.java or its javadoc
            // so at some point we have to tell them or they will see it as a bug
            throw new CommandInvalidStateException(String.format("设置 %s 只能通过API使用", setting.getName()));
        }
        if (!doingSomething && !args.hasAny()) {
            logDirect(String.format("设置 %s 的值", setting.getName()));
            logDirect(settingValueToString(setting));
        } else {
            String oldValue = settingValueToString(setting);
            if (resetting) {
                setting.reset();
            } else if (toggling) {
                if (setting.getValueClass() != Boolean.class) {
                    throw new CommandInvalidTypeException(args.consumed(), "a toggleable setting", "some other setting");
                }
                //noinspection unchecked
                Settings.Setting<Boolean> asBoolSetting = (Settings.Setting<Boolean>) setting;
                asBoolSetting.value ^= true;
                logDirect(String.format(
                        "切换设置 %s 到 %s",
                        setting.getName(),
                        Boolean.toString((Boolean) setting.value)
                ));
            } else {
                String newValue = args.getString();
                try {
                    SettingsUtil.parseAndApply(Baritone.settings(), arg, newValue);
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new CommandInvalidTypeException(args.consumed(), "a valid value", t);
                }
            }
            if (!toggling) {
                logDirect(String.format(
                        "成功将 %s %s 设置为 %s",
                        resetting ? "重置" : "设置",
                        setting.getName(),
                        settingValueToString(setting)
                ));
            }
            MutableComponent oldValueComponent = Component.literal(String.format("旧值: %s", oldValue));
            oldValueComponent.setStyle(oldValueComponent.getStyle()
                    .withColor(ChatFormatting.GRAY)
                    .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("点击将设置恢复到该值")
                    ))
                    .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            FORCE_COMMAND_PREFIX + String.format("设为 %s %s", setting.getName(), oldValue)
                    )));
            logDirect(oldValueComponent);
            if ((setting.getName().equals("chatControl") && !(Boolean) setting.value && !Baritone.settings().chatControlAnyway.value) ||
                    setting.getName().equals("chatControlAnyway") && !(Boolean) setting.value && !Baritone.settings().chatControl.value) {
                logDirect("警告: 聊天命令将不再有效. 如果你想恢复此更改, 请使用前缀控制(如果已启用)或点击上方列出的旧值", ChatFormatting.RED);
            } else if (setting.getName().equals("prefixControl") && !(Boolean) setting.value) {
                logDirect("警告: 带前缀的命令将不再有效. 如果你想撤销此更改, 请使用聊天控制(如果已启用)或点击上方列出的旧值", ChatFormatting.RED);
            }
        }
        SettingsUtil.save(Baritone.settings());
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            String arg = args.getString();
            if (args.hasExactlyOne() && !Objects.equals("save", args.peekString().toLowerCase(Locale.US))) {
                if (arg.equalsIgnoreCase("reset")) {
                    return new TabCompleteHelper()
                            .addModifiedSettings()
                            .prepend("all")
                            .filterPrefix(args.getString())
                            .stream();
                } else if (arg.equalsIgnoreCase("toggle")) {
                    return new TabCompleteHelper()
                            .addToggleableSettings()
                            .filterPrefix(args.getString())
                            .stream();
                } else if (Objects.equals("load", arg.toLowerCase(Locale.US))) {
                    // settings always use the directory of the main Minecraft instance
                    return RelativeFile.tabComplete(args, Minecraft.getInstance().gameDirectory.toPath().resolve("baritone").toFile());
                }
                Settings.Setting setting = Baritone.settings().byLowerName.get(arg.toLowerCase(Locale.US));
                if (setting != null) {
                    if (setting.getType() == Boolean.class) {
                        TabCompleteHelper helper = new TabCompleteHelper();
                        if ((Boolean) setting.value) {
                            helper.append("true", "false");
                        } else {
                            helper.append("false", "true");
                        }
                        return helper.filterPrefix(args.getString()).stream();
                    } else {
                        return Stream.of(settingValueToString(setting));
                    }
                }
            } else if (!args.hasAny()) {
                return new TabCompleteHelper()
                        .addSettings()
                        .sortAlphabetically()
                        .prepend("list", "modified", "reset", "toggle", "save", "load")
                        .filterPrefix(arg)
                        .stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "查看或更改设置";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "使用 set 命令, 你可以管理Baritone的所有设置",
                "",
                "用法:",
                "> set - 与 'set list' 相同",
                "> set list [page] - 查看所有设置",
                "> set modified [page] - 查看已修改的设置",
                "> set <setting> - 查看设置的当前值",
                "> set <setting> <value> - 设置某个设置的值",
                "> set reset all - 将所有设置重置为默认值",
                "> set reset <setting> - 将设置重置为默认值",
                "> set toggle <setting> - 切换布尔设置",
                "> set save - 保存所有设置 (不过这是自动的)",
                "> set load - 加载 settings.txt 设置",
                "> set load [filename] - 从另一个文件加载你在 Minecraft/Baritone 中的设置"
        );
    }
}
