/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.sys.fs;

import studio.phaseshift.metatron.util.MTronException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ImageHelper {

    public static String convertToAscii(final ByteBuffer buffer) {
        final StringBuilder sb = new StringBuilder("\n");
        try {
            final BufferedImage img = ImageIO.read(new ByteArrayInputStream(buffer.array()));
            double pixelValue;
            for (int i = 0; i < img.getHeight(); i++) {
                for (int j = 0; j < img.getWidth(); j++) {
                    final Color pixelColor = new Color(img.getRGB(j, i));
                    pixelValue = (pixelColor.getRed() * 0.30) +
                            (pixelColor.getBlue() * 0.59) +
                            (pixelColor.getGreen() * 0.11);
                    sb.append(strChar(pixelValue));
                }
                sb.append("{{X}}\n");
            }
            return sb.toString();
        } catch (IOException e) {
            throw MTronException.of(e);
        }
    }

    public static String strChar(final double g) {
        String pixel = " ";
        if (g >= 240) {
            pixel = " ";
        } else if (g >= 210) {
            pixel = "{{m}}.";
        } else if (g >= 190) {
            pixel = "{{y}}*";
        } else if (g >= 170) {
            pixel = "{{c}}+";
        } else if (g >= 120) {
            pixel = "{{g}}^";
        } else if (g >= 110) {
            pixel = "{{y}}&";
        } else if (g >= 80) {
            pixel = "{{w}}8";
        } else if (g >= 60) {
            pixel = "{{r}}#";
        } else {
            pixel = "{{k}}@";
        }
        return pixel;
    }
}