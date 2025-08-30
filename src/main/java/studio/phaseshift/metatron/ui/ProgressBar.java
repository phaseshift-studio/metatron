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

import static org.jline.jansi.Ansi.ansi;

public class ProgressBar {

    final int totalCount;
    int currentCount;

    public ProgressBar(int total_counts) {
        this.totalCount = total_counts;
    }

    public static ProgressBar start(final int total_counts) {
        // ansi().hide
        return new ProgressBar(total_counts);
    }


    public boolean done() {
        return this.currentCount >= this.totalCount;
    }

    public void end(final String end_message) {
        this.currentCount = this.totalCount;
        this.incr(end_message);
    }

    public void incr(final String message) {
        float percentage =
                0 == this.currentCount
                        ? 0
                        : (((float) (this.currentCount) / ((float) (this.totalCount))) * 100.f);
        ++this.currentCount;
        if (percentage >= 100) {
            percentage = 100;
           // System.out.print(ansi().eraseLine());
        }


        System.out.print("!g[INFO]  [!b");
        for (int j = 0; j < (int) percentage; j = j + 2) {
            // + 2 to make bar half as long
            System.out.print("#");
        }
        for (int j = (int) percentage; j < 99; j = j + 2) {
            System.out.print(' ');
        }
        System.out.print("!g] !y%d%%!! %-25s\r".formatted((int) percentage, message));
        try {
            Thread.sleep(250);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}