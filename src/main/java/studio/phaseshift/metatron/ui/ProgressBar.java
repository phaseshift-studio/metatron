/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.ui;

public class ProgressBar {

    final int totalCount;
    int currentCount;

    public ProgressBar(int totalCount) {
        this.totalCount = totalCount;
    }

    public <T> T incr(final T message) {
        float percentage =
                0 == this.currentCount
                        ? 0
                        : (((float) (this.currentCount) / ((float) (this.totalCount))) * 100.f);
        ++this.currentCount;
        if (percentage >= 100) {
            percentage = 100;
            Graphitty.stdout().clearLine();
            Graphitty.stdout().println(message.toString());
        }


        Graphitty.stdout().print("!g[INFO]  [!b");
        for (int j = 0; j < (int) percentage; j = j + 2) {
            // + 2 to make bar half as long
            Graphitty.stdout().print("#");
        }
        for (int j = (int) percentage; j < 99; j = j + 2) {
            Graphitty.stdout().print(' ');
        }
        Graphitty.stdout().print("!g] !y%d%%!! %-25s\r".formatted((int) percentage, message));
        try {
            Thread.sleep(10);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return message;
    }
}