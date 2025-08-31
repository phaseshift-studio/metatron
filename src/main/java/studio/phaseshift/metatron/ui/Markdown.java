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

public class Markdown {

    public static String markdown(final String md) {
        return md
                // Bold
                .replaceAll("\\*\\*(.*?)\\*\\*", "\u001B[1m$1\u001B[0m")
                // Italic
                .replaceAll("\\*(.*?)\\*", "\u001B[3m$1\u001B[0m")
                // Underline
                .replaceAll("__(.*?)__", "\u001B[4m$1\u001B[0m")
                // Strikethrough
                .replaceAll("~~(.*?)~~", "\u001B[9m$1\u001B[0m")
                // Blockquote
                .replaceAll("(> ?.*)",
                        "\u001B[3m\u001B[34m\u001B[1m$1\u001B[22m\u001B[0m")
                // Lists (bold magenta number and bullet)
                .replaceAll("([\\d]+\\.|-|\\*) (.*)",
                        "\u001B[35m\u001B[1m$1\u001B[22m\u001B[0m $2")
                // Block code (black on gray)
                .replaceAll("(?s)```(\\w+)?\\n(.*?)\\n```",
                        "\u001B[3m\u001B[1m$1\u001B[22m\u001B[0m\n\u001B[57;107m$2\u001B[0m\n")
                // Inline code (black on gray)
                .replaceAll("`(.*?)`", "\u001B[57;107m$1\u001B[0m")
                // Headers (cyan bold)
                .replaceAll("(#{1,6}) (.*?)\n",
                        "\u001B[36m\u001B[1m$1 $2\u001B[22m\u001B[0m\n")
                // Headers with a single line of text followed by 2 or more equal signs
                .replaceAll("(.*?\n={2,}\n)",
                        "\u001B[36m\u001B[1m$1\u001B[22m\u001B[0m\n")
                // Headers with a single line of text followed by 2 or more dashes
                .replaceAll("(.*?\n-{2,}\n)",
                        "\u001B[36m\u001B[1m$1\u001B[22m\u001B[0m\n")
                // Images (blue underlined)
                .replaceAll("!\\[(.*?)]\\((.*?)\\)",
                        "\u001B[34m$1\u001B[0m (\u001B[34m\u001B[4m$2\u001B[0m)")
                // Links (blue underlined)
                .replaceAll("!?\\[(.*?)]\\((.*?)\\)",
                        "\u001B[34m$1\u001B[0m (\u001B[34m\u001B[4m$2\u001B[0m)");
    }
}
