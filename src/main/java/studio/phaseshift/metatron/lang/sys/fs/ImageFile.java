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

public class ImageFile {

    public static String convertToAscii(final ByteBuffer buffer) {
       // printToString(buffer.duplicate());
        final StringBuilder sb = new StringBuilder("\n");
        try {
            final BufferedImage img = ImageIO.read(new ByteArrayInputStream(buffer.array()));
            double pixval;
            for (int i = 0; i < img.getHeight(); i++) {
                for (int j = 0; j < img.getWidth(); j++) {
                    final Color pixcol = new Color(img.getRGB(j, i));
                    pixval = (((pixcol.getRed() * 0.30) + (pixcol.getBlue() * 0.59) + (pixcol
                            .getGreen() * 0.11)));
                    sb.append(strChar(pixval));
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            throw MTronException.of(e);
        }
    }

    
    public static void printToString(final ByteBuffer buffer) {
        try {

            int h = 0;
            int w = 0;
            final BufferedImage image = ImageIO.read(new ByteArrayInputStream(buffer.array()));
            String colorFormat = "8Colors";

            for (int i = 1; i < 9000000; i++) {

                if (i - 1 == image.getWidth() * image.getHeight()) {
                    break;
                }

                if (i == 1) {
                    w = 0;
                } else {
                    w = w + 1;
                }

                if (image.getWidth() == w) {
                    h = h + 1;
                    w = 0;
                    System.out.printf("\n");
                }

                int clr = image.getRGB(w, h);
                int red = (clr & 0x00ff0000) >> 16;
                int green = (clr & 0x0000ff00) >> 8;
                int blue = clr & 0x000000ff;

                ColorFormats colorFormats = new ColorFormats();

                int colornum = 30;

                switch (colorFormat) {
                    case "8Colors":
                        colornum = colorFormats.get8Color(red, green, blue);
                        break;
                }

                if ((clr >> 24) == 0x00) {
                    System.out.printf("  ");
                } else {
                    System.out.printf("\33[" + colornum + "██");
                }


            }
        } catch (final Exception e) {
            throw MTronException.of(e);
        }

    }

    public static String strChar(final double g) {
        String str = " ";
        if (g >= 240) {
            str = " ";
        } else if (g >= 210) {
            str = ".";
        } else if (g >= 190) {
            str = "*";
        } else if (g >= 170) {
            str = "+";
        } else if (g >= 120) {
            str = "^";
        } else if (g >= 110) {
            str = "&";
        } else if (g >= 80) {
            str = "8";
        } else if (g >= 60) {
            str = "#";
        } else {
            str = "@";
        }
        return str;
    }


    public static class ColorFormats {

        public int get8Color(int red, int green, int blue) {


        /*int[] r8 = {0,  255,  0,    255,  0,    255,  0,    255};
        int[] g8 = {0,  0,    255,  255,  0,    0,    255,  255};
        int[] b8 = {0,  0,    0,    0,    255,  255,  255,  255};*/

            String[] colors = {"000", "25500", "02550", "2552550", "00255", "2550255", "0255255", "255255255"};

            int r8 = 0;
            int g8 = 0;
            int b8 = 0;

            if (red > 127) {
                r8 = 255;
            }
            if (green > 127) {
                g8 = 255;
            }
            if (blue > 127) {
                b8 = 255;
            }

            String xcolor = Integer.toString(r8) + String.valueOf(g8) + String.valueOf(b8);

            int color = 1;
        /*for (int i=0; i<colors.length; i++){

            if(colors[i] == xcolor){
                color = 30+i;
            }
        }*/

            switch (xcolor) {
                case "000": //black
                    color = 30;
                    break;

                case "25500": //red
                    color = 31;
                    break;

                case "02550": //green
                    color = 32;
                    break;

                case "2552550": //yellow
                    color = 33;
                    break;

                case "00255": //blue
                    color = 34;
                    break;

                case "2550255": //magenta
                    color = 35;
                    break;

                case "0255255": //cyan
                    color = 36;
                    break;

                case "255255255": //white / light gray
                    color = 27;
                    break;
            }

            return color;
        }
    }
}