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
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IElytraProcess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ElytraCommand extends Command {

    public ElytraCommand(IBaritone baritone) {
        super(baritone, "elytra");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        final ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
        final IElytraProcess elytra = baritone.getElytraProcess();
        if (args.hasExactlyOne() && args.peekString().equals("支持")) {
            logDirect(elytra.isLoaded() ? "支持此设备" : unsupportedSystemMessage());
            return;
        }
        if (!elytra.isLoaded()) {
            throw new CommandInvalidStateException(unsupportedSystemMessage());
        }

        if (!args.hasAny()) {
            if (Baritone.settings().elytraTermsAccepted.value) {
                gatekeep();
            }
            Goal iGoal = customGoalProcess.mostRecentGoal();
            if (iGoal == null) {
                throw new CommandInvalidStateException("尚未设定任何目标");
            }
            if (ctx.world().dimension() != Level.NETHER) {
                throw new CommandInvalidStateException("只在下界有效");
            }
            try {
                elytra.pathTo(iGoal);
            } catch (IllegalArgumentException ex) {
                throw new CommandInvalidStateException(ex.getMessage());
            }
            return;
        }

        final String action = args.getString();
        switch (action) {
            case "reset": {
                elytra.resetState();
                logDirect("状态重置, 但仍然飞向同一目标");
                break;
            }
            case "repack": {
                elytra.repackChunks();
                logDirect("已将所有加载的区块排队以重新打包");
                break;
            }
            default: {
                throw new CommandInvalidStateException("无效动作");
            }
        }
    }

    private void gatekeep() {
        MutableComponent gatekeep = Component.literal("");
        gatekeep.append("要禁用此消息, 请启用 elytraTermsAccepted 设置\n");
        MutableComponent gatekeep2 = Component.literal("如果希望Baritone尝试从地面起飞, 你可以启用elytraAutoJump\n");
        gatekeep2.setStyle(gatekeep2.getStyle().withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(Baritone.settings().prefix.value + "set elytraAutoJump true"))));
        gatekeep.append(gatekeep2);
        MutableComponent gatekeep3 = Component.literal("如果希望Baritone飞得更慢, 请启用elytraConserveFireworks或降低elytraFireworkSpeed\n");
        gatekeep3.setStyle(gatekeep3.getStyle().withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(Baritone.settings().prefix.value + "set elytraConserveFireworks true\n" + Baritone.settings().prefix.value + "set elytraFireworkSpeed 0.6\n(0.6 这个数字只是一个例子, 可以根据你的喜好调整)"))));
        gatekeep.append(gatekeep3);
        MutableComponent gatekeep4 = Component.literal("Baritone Elytra ");
        MutableComponent red = Component.literal("想知道你所处世界的种子");
        red.setStyle(red.getStyle().withColor(ChatFormatting.RED).withUnderlined(true).withBold(true));
        gatekeep4.append(red);
        gatekeep4.append(", 如果它没有正确的种子, 它经常会回溯. 它使用种子生成你看不到的远处地形, 因为下界的地形障碍可能比你的渲染距离大得多");
        gatekeep.append(gatekeep4);
        gatekeep.append("\n");
        if (Baritone.settings().elytraPredictTerrain.value) {
            MutableComponent gatekeep5 = Component.literal("Baritone鞘翅预测地形时假设 " + Baritone.settings().elytraNetherSeed.value + " 是正确的种子, 将它改为 " + Baritone.settings().prefix.value + "set elytraNetherSeed seed, 或者用 " + Baritone.settings().prefix.value + "set elytraPredictTerrain false");
            gatekeep.append(gatekeep5);
        } else {
            MutableComponent gatekeep5 = Component.literal("Baritone鞘翅并不是在预测地形. 如果你不知道种子, 这样做是正确的. 如果你知道种子, 输入如下: " + Baritone.settings().prefix.value + "set elytraNetherSeed seed, 然后用 " + Baritone.settings().prefix.value + "set elytraPredictTerrain true");
            gatekeep.append(gatekeep5);
        }
        logDirect(gatekeep);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        TabCompleteHelper helper = new TabCompleteHelper();
        if (args.hasExactlyOne()) {
            helper.append("reset", "repack", "supported");
        }
        return helper.filterPrefix(args.getString()).stream();
    }

    @Override
    public String getShortDesc() {
        return "鞘翅时间";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "鞘翅指令告诉Baritone在下界自动飞向当前目标",
                "",
                "用法:",
                "> elytra - 飞到当前目标",
                "> elytra reset - 重置状态, 但会尝试继续飞向同一目标",
                "> elytra repack - 将渲染距离内的所有区块排队, 以提供给本地库",
                "> elytra supported - 告诉你Baritone是否提供与你的电脑兼容的本地库"
        );
    }

    private static String unsupportedSystemMessage() {
        final String osArch = System.getProperty("os.arch");
        final String osName = System.getProperty("os.name");
        return String.format(
                "加载本地库失败. 您的 CPU 是 %s, 操作系统是 %s" +
                        "支持的架构有64位x86和64位ARM, 支持的操作系统有Windows、Linux和Mac",
                osArch, osName
        );
    }
}
