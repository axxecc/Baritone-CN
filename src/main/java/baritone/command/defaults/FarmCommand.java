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

import baritone.api.IBaritone;
import baritone.api.cache.IWaypoint;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.ForWaypoints;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.utils.BetterBlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class FarmCommand extends Command {

    public FarmCommand(IBaritone baritone) {
        super(baritone, "farm");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);
        int range = 0;
        BetterBlockPos origin = null;
        //range
        if (args.has(1)) {
            range = args.getAs(Integer.class);
        }
        //waypoint
        if (args.has(1)) {
            IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            IWaypoint waypoint = null;
            switch (waypoints.length) {
                case 0:
                    throw new CommandInvalidStateException("未发现任何路径点");
                case 1:
                    waypoint = waypoints[0];
                    break;
                default:
                    throw new CommandInvalidStateException("发现了多个路径点");
            }
            origin = waypoint.getLocation();
        }

        baritone.getFarmProcess().farm(range, origin);
        logDirect("耕作");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "种植附近的农作物";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "农场指令开始耕种附近的植物, 它收获成熟作物并种植新作物",
                "",
                "用法:",
                "> farm - 种下能找到的各种作物",
                "> farm <range> - 从起始位置到范围内种植作物",
                "> farm <range> <waypoint> - 在路径点范围内种植作物"
        );
    }
}
