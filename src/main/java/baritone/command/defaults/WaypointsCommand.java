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
import baritone.api.cache.IWaypoint;
import baritone.api.cache.IWorldData;
import baritone.api.cache.Waypoint;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.ForWaypoints;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.helpers.Paginator;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class WaypointsCommand extends Command {

    private Map<IWorldData, List<IWaypoint>> deletedWaypoints = new HashMap<>();

    public WaypointsCommand(IBaritone baritone) {
        super(baritone, "waypoint");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Action action = args.hasAny() ? Action.getByName(args.getString()) : Action.LIST;
        if (action == null) {
            throw new CommandInvalidTypeException(args.consumed(), "an action");
        }
        BiFunction<IWaypoint, Action, Component> toComponent = (waypoint, _action) -> {
            MutableComponent component = Component.literal("");
            MutableComponent tagComponent = Component.literal(waypoint.getTag().name() + " ");
            tagComponent.setStyle(tagComponent.getStyle().withColor(ChatFormatting.GRAY));
            String name = waypoint.getName();
            MutableComponent nameComponent = Component.literal(!name.isEmpty() ? name : "<empty>");
            nameComponent.setStyle(nameComponent.getStyle().withColor(!name.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
            MutableComponent timestamp = Component.literal(" @ " + new Date(waypoint.getCreationTimestamp()));
            timestamp.setStyle(timestamp.getStyle().withColor(ChatFormatting.DARK_GRAY));
            component.append(tagComponent);
            component.append(nameComponent);
            component.append(timestamp);
            component.setStyle(component.getStyle()
                    .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("点击选择")
                    ))
                    .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s %s %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    _action.names[0],
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp()
                            ))
                    ));
            return component;
        };
        Function<IWaypoint, Component> transform = waypoint ->
                toComponent.apply(waypoint, action == Action.LIST ? Action.INFO : action);
        if (action == Action.LIST) {
            IWaypoint.Tag tag = args.hasAny() ? IWaypoint.Tag.getByName(args.peekString()) : null;
            if (tag != null) {
                args.get();
            }
            IWaypoint[] waypoints = tag != null
                    ? ForWaypoints.getWaypointsByTag(this.baritone, tag)
                    : ForWaypoints.getWaypoints(this.baritone);
            if (waypoints.length > 0) {
                args.requireMax(1);
                Paginator.paginate(
                        args,
                        waypoints,
                        () -> logDirect(
                                tag != null
                                        ? String.format("所有标签为 %s 的路径点:", tag.name())
                                        : "所有路径点:"
                        ),
                        transform,
                        String.format(
                                "%s%s %s%s",
                                FORCE_COMMAND_PREFIX,
                                label,
                                action.names[0],
                                tag != null ? " " + tag.getName() : ""
                        )
                );
            } else {
                args.requireMax(0);
                throw new CommandInvalidStateException(
                        tag != null
                                ? "未找到具有该标签的路径点"
                                : "未找到路径点"
                );
            }
        } else if (action == Action.SAVE) {
            IWaypoint.Tag tag = args.hasAny() ? IWaypoint.Tag.getByName(args.peekString()) : null;
            if (tag == null) {
                tag = IWaypoint.Tag.USER;
            } else {
                args.get();
            }
            String name = (args.hasExactlyOne() || args.hasExactly(4)) ? args.getString() : "";
            BetterBlockPos pos = args.hasAny()
                    ? args.getDatatypePost(RelativeBlockPos.INSTANCE, ctx.playerFeet())
                    : ctx.playerFeet();
            args.requireMax(0);
            IWaypoint waypoint = new Waypoint(name, tag, pos);
            ForWaypoints.waypoints(this.baritone).addWaypoint(waypoint);
            MutableComponent component = Component.literal("已添加路径点: ");
            component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));
            component.append(toComponent.apply(waypoint, Action.INFO));
            logDirect(component);
        } else if (action == Action.CLEAR) {
            args.requireMax(1);
            String name = args.getString();
            IWaypoint.Tag tag = IWaypoint.Tag.getByName(name);
            if (tag == null) {
                throw new CommandInvalidStateException("无效标签, \"" + name + "\"");
            }
            IWaypoint[] waypoints = ForWaypoints.getWaypointsByTag(this.baritone, tag);
            for (IWaypoint waypoint : waypoints) {
                ForWaypoints.waypoints(this.baritone).removeWaypoint(waypoint);
            }
            deletedWaypoints.computeIfAbsent(baritone.getWorldProvider().getCurrentWorld(), k -> new ArrayList<>()).addAll(Arrays.<IWaypoint>asList(waypoints));
            MutableComponent textComponent = Component.literal(String.format("已清除 %d 个路径点, 点击恢复它们", waypoints.length));
            textComponent.setStyle(textComponent.getStyle().withClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    String.format(
                            "%s%s 恢复 @ %s",
                            FORCE_COMMAND_PREFIX,
                            label,
                            Stream.of(waypoints).map(wp -> Long.toString(wp.getCreationTimestamp())).collect(Collectors.joining(" "))
                    )
            )));
            logDirect(textComponent);
        } else if (action == Action.RESTORE) {
            List<IWaypoint> waypoints = new ArrayList<>();
            List<IWaypoint> deletedWaypoints = this.deletedWaypoints.getOrDefault(baritone.getWorldProvider().getCurrentWorld(), Collections.emptyList());
            if (args.peekString().equals("@")) {
                args.get();
                // no args.requireMin(1) because if the user clears an empty tag there is nothing to restore
                while (args.hasAny()) {
                    long timestamp = args.getAs(Long.class);
                    for (IWaypoint waypoint : deletedWaypoints) {
                        if (waypoint.getCreationTimestamp() == timestamp) {
                            waypoints.add(waypoint);
                            break;
                        }
                    }
                }
            } else {
                args.requireExactly(1);
                int size = deletedWaypoints.size();
                int amount = Math.min(size, args.getAs(Integer.class));
                waypoints = new ArrayList<>(deletedWaypoints.subList(size - amount, size));
            }
            waypoints.forEach(ForWaypoints.waypoints(this.baritone)::addWaypoint);
            deletedWaypoints.removeIf(waypoints::contains);
            logDirect(String.format("已恢复 %d 个路径点", waypoints.size()));
        } else {
            IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            IWaypoint waypoint = null;
            if (args.hasAny() && args.peekString().equals("@")) {
                args.requireExactly(2);
                args.get();
                long timestamp = args.getAs(Long.class);
                for (IWaypoint iWaypoint : waypoints) {
                    if (iWaypoint.getCreationTimestamp() == timestamp) {
                        waypoint = iWaypoint;
                        break;
                    }
                }
                if (waypoint == null) {
                    throw new CommandInvalidStateException("已指定时间戳, 但未找到路径点");
                }
            } else {
                switch (waypoints.length) {
                    case 0:
                        throw new CommandInvalidStateException("未找到路径点");
                    case 1:
                        waypoint = waypoints[0];
                        break;
                    default:
                        break;
                }
            }
            if (waypoint == null) {
                args.requireMax(1);
                Paginator.paginate(
                        args,
                        waypoints,
                        () -> logDirect("找到多个路径点:"),
                        transform,
                        String.format(
                                "%s%s %s %s",
                                FORCE_COMMAND_PREFIX,
                                label,
                                action.names[0],
                                args.consumedString()
                        )
                );
            } else {
                if (action == Action.INFO) {
                    logDirect(transform.apply(waypoint));
                    logDirect(String.format("位置: %s", waypoint.getLocation()));
                    MutableComponent deleteComponent = Component.literal("点击删除此路径点");
                    deleteComponent.setStyle(deleteComponent.getStyle().withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s 删除 %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp()
                            )
                    )));
                    MutableComponent goalComponent = Component.literal("点击将目标设置为此路径点");
                    goalComponent.setStyle(goalComponent.getStyle().withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s 目标 %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp()
                            )
                    )));
                    MutableComponent recreateComponent = Component.literal("点击显示重新创建此路径点的命令");
                    recreateComponent.setStyle(recreateComponent.getStyle().withClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            String.format(
                                    "%s%s 保存 %s %s %s %s %s",
                                    Baritone.settings().prefix.value, // This uses the normal prefix because it is run by the user.
                                    label,
                                    waypoint.getTag().getName(),
                                    waypoint.getName(),
                                    waypoint.getLocation().x,
                                    waypoint.getLocation().y,
                                    waypoint.getLocation().z
                            )
                    )));
                    MutableComponent backComponent = Component.literal("点击返回路径点列表");
                    backComponent.setStyle(backComponent.getStyle().withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s 列表",
                                    FORCE_COMMAND_PREFIX,
                                    label
                            )
                    )));
                    logDirect(deleteComponent);
                    logDirect(goalComponent);
                    logDirect(recreateComponent);
                    logDirect(backComponent);
                } else if (action == Action.DELETE) {
                    ForWaypoints.waypoints(this.baritone).removeWaypoint(waypoint);
                    deletedWaypoints.computeIfAbsent(baritone.getWorldProvider().getCurrentWorld(), k -> new ArrayList<>()).add(waypoint);
                    MutableComponent textComponent = Component.literal("该路径点已成功删除, 点击可恢复");
                    textComponent.setStyle(textComponent.getStyle().withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s 恢复 @ %s",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    waypoint.getCreationTimestamp()
                            )
                    )));
                    logDirect(textComponent);
                } else if (action == Action.GOAL) {
                    Goal goal = new GoalBlock(waypoint.getLocation());
                    baritone.getCustomGoalProcess().setGoal(goal);
                    logDirect(String.format("目标: %s", goal));
                } else if (action == Action.GOTO) {
                    Goal goal = new GoalBlock(waypoint.getLocation());
                    baritone.getCustomGoalProcess().setGoalAndPath(goal);
                    logDirect(String.format("前往: %s", goal));
                }
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            if (args.hasExactlyOne()) {
                return new TabCompleteHelper()
                        .append(Action.getAllNames())
                        .sortAlphabetically()
                        .filterPrefix(args.getString())
                        .stream();
            } else {
                Action action = Action.getByName(args.getString());
                if (args.hasExactlyOne()) {
                    if (action == Action.LIST || action == Action.SAVE || action == Action.CLEAR) {
                        return new TabCompleteHelper()
                                .append(IWaypoint.Tag.getAllNames())
                                .sortAlphabetically()
                                .filterPrefix(args.getString())
                                .stream();
                    } else if (action == Action.RESTORE) {
                        return Stream.empty();
                    } else {
                        return args.tabCompleteDatatype(ForWaypoints.INSTANCE);
                    }
                } else if (args.has(3) && action == Action.SAVE) {
                    args.get();
                    args.get();
                    return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
                }
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "管理路径点";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "路径点命令允许你管理Baritone的路径点",
                "",
                "路径点可用于标记以后要使用的位置, 每个路径点都有一个标签和一个可选名称",
                "",
                "请注意, info,delete和goal命令允许你通过标签指定路径点. 如果某个标签对应多个路径点, 这些命令将让你选择你指的是哪个路径点",
                "",
                "保存命令缺少参数, 使用 USER 标签, 将创建一个未命名的路径点, 并以你当前位置作为默认值",
                "",
                "用法:",
                "> waypoint [list] - 列出所有路径点",
                "> waypoint <list> <tag> - 按标签列出所有路径点",
                "> waypoint <save> - 在当前位置保存一个未命名的用户路径点",
                "> waypoint <save> [tag] [name] [pos] - 保存具有指定标签, 名称和位置的路径点",
                "> waypoint <info/show> <tag/name> - 按标签或名称显示路径点信息",
                "> waypoint <delete> <tag/name> - 通过标签或名称删除路径点",
                "> waypoint <restore> <n> - 恢复最近删除的n个路径点",
                "> waypoint <clear> <tag> - 删除所有具有指定标签的路径点",
                "> waypoint <goal> <tag/name> - 通过标签或名称将目标设置为路径点",
                "> waypoint <goto> <tag/name> - 通过标签或名称将目标设置为一个路径点并开始路径规划"
        );
    }

    private enum Action {
        LIST("list"),
        CLEAR("clear"),
        SAVE("save"),
        INFO("info"),
        DELETE("delete"),
        RESTORE("restore"),
        GOAL("goal"),
        GOTO("goto");
        private final String[] names;

        Action(String... names) {
            this.names = names;
        }

        public static Action getByName(String name) {
            for (Action action : Action.values()) {
                for (String alias : action.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return action;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (Action action : Action.values()) {
                names.addAll(Arrays.asList(action.names));
            }
            return names.toArray(new String[0]);
        }
    }
}
