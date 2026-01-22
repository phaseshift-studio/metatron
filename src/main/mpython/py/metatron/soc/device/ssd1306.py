#  Metatron: A Distributed Computing Language and Virtual Machine
#   Copyright (C) 2025- PhaseShift Studio, LLC
# 
#  This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Affero General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
# 
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Affero General Public License for more details.
# 
#  You should have received a copy of the GNU Affero General Public License
#  along with this program.  If not, see <http://www.gnu.org/licenses/>.

# MicroPython SSD1306 OLED driver, I2C and SPI interfaces created by Adafruit

import framebuf
import math
import time

from metatron.furi import f, fURI
from metatron.obj import Obj
from metatron.soc.device.device import Device
from metatron.util.graphitty import LOG
from metatron.util.mach import router

# register definitions
SET_CONTRAST = const(0x81)
SET_ENTIRE_ON = const(0xa4)
SET_NORM_INV = const(0xa6)
SET_DISP = const(0xae)
SET_MEM_ADDR = const(0x20)
SET_COL_ADDR = const(0x21)
SET_PAGE_ADDR = const(0x22)
SET_DISP_START_LINE = const(0x40)
SET_SEG_REMAP = const(0xa0)
SET_MUX_RATIO = const(0xa8)
SET_COM_OUT_DIR = const(0xc0)
SET_DISP_OFFSET = const(0xd3)
SET_COM_PIN_CFG = const(0xda)
SET_DISP_CLK_DIV = const(0xd5)
SET_PRECHARGE = const(0xd9)
SET_VCOM_DESEL = const(0xdb)
SET_CHARGE_PUMP = const(0x8d)

SSD1306_TID = f("/soc/ssd1306")


class Ssd1306(Device):
    def __init__(self, i2c: Device, addr, height: int, width: int, soc_vid, name='ssd1306'):
        Device.__init__(self, soc_vid, {'addr': addr, 'width': width, 'height': height}, SSD1306_TID, name)
        self.i2c = i2c.pvm
        self.addr = addr
        self.temp = bytearray(2)
        # Add an extra byte to the data buffer to hold an I2C data/command byte
        # to use hardware-compatible I2C transactions.  A memoryview of the
        # buffer is used to mask this byte from the framebuffer operations
        # (without a major memory hit as memoryview doesn't copy to a separate
        # buffer).
        self.buffer = bytearray(((height // 8) * width) + 1)
        self.buffer[0] = 0x40  # Set first byte of data buffer to Co=0, D/C=1
        self.framebuf = framebuf.FrameBuffer1(memoryview(self.buffer)[1:], width, height)
        self.width = width
        self.height = height
        self.pages = self.height // 8

    def start(self) -> 'Ssd1306':
        for cmd in (
                SET_DISP | 0x00,  # off
                # address setting
                SET_MEM_ADDR, 0x00,  # horizontal
                # resolution and layout
                SET_DISP_START_LINE | 0x00,
                SET_SEG_REMAP | 0x01,  # column addr 127 mapped to SEG0
                SET_MUX_RATIO, self.height - 1,
                SET_COM_OUT_DIR | 0x08,  # scan from COM[N] to COM0
                SET_DISP_OFFSET, 0x00,
                SET_COM_PIN_CFG, 0x02 if self.height == 32 else 0x12,
                # timing and driving scheme
                SET_DISP_CLK_DIV, 0x80,
                SET_PRECHARGE, 0xf1,  # no external vcc
                SET_VCOM_DESEL, 0x30,  # 0.83*Vcc
                # display
                SET_CONTRAST, 0xff,  # maximum
                SET_ENTIRE_ON,  # output follows RAM contents
                SET_NORM_INV,  # not inverted
                # charge pump
                SET_CHARGE_PUMP, 0x14,  # no external vcc
                SET_DISP | 0x01):  # on
            self._write_cmd(cmd)
        self.fill(0)
        self.sline(0, 15, True, self.width, 1, False)
        self.sline(0, 0, True, self.width, 1, False)
        self.sline(0, 0, False, 15, 1, False)
        self.sline(self.width - 1, 0, False, 15, 1, False)
        ######################################################################
        self.rotated_rectangle(40, 40, 25, 30, 55, 1, False)
        self.sline(50,40,True,45,1,False)
        # self.sline(0, self.width, False, self.height, 1, False)
        self.text("metatron v0.1", 12, 5, 1, False)
        self.text(" ..... ", 44, 29, 1, False)
        self.text("|     |", 44, 42, 1, False)
        self.text("|_|_|_|", 44, 53, 1, False)
        self.show()
        # self.image('mtron_logo.pbm')
        if self.soc_vid is not None:
            router().subscribe(self.soc_vid.extend(self.name).extend("+"),
                               lambda vid, value: Ssd1306._process_cmd(self, vid, value))
        return self

    @staticmethod
    def _process_cmd(device: 'Ssd1306', vid: fURI, value: Obj):
        if hasattr(device, vid.name()):
            try:
                value = value.pvm if isinstance(value, Obj) else value
                if isinstance(value, dict):
                    args = {}
                    for key, val in value.items():
                        key = key if isinstance(key, str) else str(key)
                        args[key] = val.pvm if isinstance(val, Obj) else val
                    LOG.info("calling {{y}}{}{{X}} with {}", vid.name(), args)
                    getattr(device, vid.name())(**args)
                elif isinstance(value, list):
                    args = []
                    for val in value:
                        args.append(val.pvm if isinstance(val, Obj) else val)
                    LOG.info("calling {{y}}{}{{X}} with {}", vid.name(), args)
                    getattr(device, vid.name())(*args)
            except Exception as e:
                LOG.error("error calling method {{r}}{}{{X}} on {{y}}{}{{X}}: {}", vid.name(), vid, e)
        else:
            LOG.error("unknown method {{r}}{}{{X}} on {{y}}{}", vid.name(), vid)

    def _write_cmd(self, cmd):
        self.temp[0] = 0x80  # Co=1, D/C#=0
        self.temp[1] = cmd
        self.i2c.writeto(self.addr, self.temp)

    def stop(self) -> 'Ssd1306':
        Device.stop(self)
        self._write_cmd(SET_DISP | 0x00)
        return self

    def show(self):
        x0 = 0
        x1 = self.width - 1
        if self.width == 64:
            x0 += 32
            x1 += 32
        self._write_cmd(SET_COL_ADDR)
        self._write_cmd(x0)
        self._write_cmd(x1)
        self._write_cmd(SET_PAGE_ADDR)
        self._write_cmd(0)
        self._write_cmd(self.pages - 1)
        self.i2c.writeto(self.addr, self.buffer)

    def clear(self, show=True):
        self.fill(0, show)

    def fill(self, col: int = 1, show=True):
        self.framebuf.fill(col)
        if show:
            self.show()

    def pixel(self, x: int, y: int, col: int = 1, show=True):
        self.framebuf.pixel(x, y, col)
        if show:
            self.show()

    def line(self, x1: int, y1: int, x2: int, y2: int, col: int = 1, show=True):
        self.framebuf.line(x1, y1, x2, y2, col)
        if show:
            self.show()

    def sline(self, x: int, y: int, horizontal: bool, length: int = -1, col: int = 1, show=True):
        if length is -1:
            length = self.width if horizontal else self.height
        if horizontal:
            self.framebuf.hline(x, y, length, col)
        else:
            self.framebuf.vline(x, y, length, col)
        if show:
            self.show()

    def square(self, x: int, y: int, size: int, col: int = 1, show: bool = True):
        for i in range(x, x + size):
            for j in range(y, y + size):
                self.framebuf.pixel(i, j, col)
        if show:
            self.show()

    def circle(self, x0: int, y0: int, radius: int, col: int = 1, show: bool = True):
        x = radius
        y = 0
        err = 0
        while x >= y:
            self.framebuf.pixel(x0 + x, y0 + y, col)
            self.framebuf.pixel(x0 + y, y0 + x, col)
            self.framebuf.pixel(x0 - y, y0 + x, col)
            self.framebuf.pixel(x0 - x, y0 + y, col)
            self.framebuf.pixel(x0 - x, y0 - y, col)
            self.framebuf.pixel(x0 - y, y0 - x, col)
            self.framebuf.pixel(x0 + y, y0 - x, col)
            self.framebuf.pixel(x0 + x, y0 - y, col)
            y += 1
            if err <= 0:
                err += 2 * y + 1
            if err > 0:
                x -= 1
                err -= 2 * x + 1
        if show:
            self.show()

    def scroll(self, dx: int, dy: int, show: bool = True):
        self.framebuf.scroll(dx, dy)
        if show:
            self.show()

    def contrast(self, contrast: int, show: bool = True):
        self._write_cmd(SET_CONTRAST)
        self._write_cmd(contrast)
        if show:
            self.show()

    def invert(self, invert: bool, show: bool = True):
        self._write_cmd(SET_NORM_INV | (invert & 1))
        if show:
            self.show()

    def text(self, text: str, x: int, y: int, col: int = 1, show: bool = True):
        self.framebuf.text(text, x, y, col)
        if show:
            self.show()

    def image(self, filename: str, show: bool = True):
        with open(filename, 'rb') as image_file:
            image_data = image_file.read()
        fb = framebuf.FrameBuffer(bytearray(image_data), ((self.width + 7) // 8) * 8, self.height, framebuf.MONO_HLSB)
        self.framebuf.blit(fb, 0, 0)
        if show:
            self.show()

    def rotated_rectangle(self, x: int, y: int, w: int, h: int, angle: int, col: int = 1, show: bool = True):
        angle_rad = math.radians(angle)
        # Half-width and half-height for corner calculation
        hw = w / 2
        hh = h / 2
        # Define the four corners relative to center
        corners = [
            (-hw, -hh),  # Top-left
            (hw, -hh),  # Top-right
            (hw, hh),  # Bottom-right
            (-hw, hh)  # Bottom-left
        ]
        # Rotate each corner
        rotated_corners = []
        for xx, yy in corners:
            cos_a = math.cos(angle_rad)
            sin_a = math.sin(angle_rad)
            rx = x + xx * cos_a - yy * sin_a
            ry = y + yy * sin_a + yy * cos_a
            rotated_corners.append((int(rx), int(ry)))
        # Draw lines between the rotated corners
        self.line(rotated_corners[0][0], rotated_corners[0][1], rotated_corners[1][0], rotated_corners[1][1], col)
        self.line(rotated_corners[1][0], rotated_corners[1][1], rotated_corners[2][0], rotated_corners[2][1], col)
        self.line(rotated_corners[2][0], rotated_corners[2][1], rotated_corners[3][0], rotated_corners[3][1], col)
        self.line(rotated_corners[3][0], rotated_corners[3][1], rotated_corners[0][0], rotated_corners[0][1], col)
        if show:
            self.show()
