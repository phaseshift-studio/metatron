#  metatron: a distributed virtual machine and language
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

from machine import Pin, SPI
from os import uname

from metatron.furi import f
from metatron.soc.device.device import Device
from metatron.soc.device.spi import Spi
from metatron.util.graphitty import LOG

MFRC522_TID = f("/soc/mfrc522")
MAX_LEN							= 16
CALCULATE_CRC					= 0x03
ANTICOLL						= 0x93
SELECT_TAG						= 0x93
TRANSCEIVE						= 0x0C
AUTHENTICATE					= 0x0E
READ							= 0x30
WRITE							= 0xA0

MFRC522_COMMAND_REG				= 0x01
MFRC522_COML_EN_REG				= 0x02
MFRC522_DIVL_EN_REG				= 0x03
MFRC522_COM_IRQ_REG				= 0x04
MFRC522_DIV_IRQ_REG				= 0x05
MFRC522_ERROR_REG				= 0x06
MFRC522_STATUS_2_REG			= 0x08
MFRC522_FIFO_DATA_REG			= 0x09
MFRC522_FIFO_LEVEL_REG			= 0x0A
MFRC522_CONTROL_REG 			= 0x0C
MFRC522_BIT_FRAMING_REG 		= 0x0D
MFRC522_MODE_REG				= 0x11
MFRC522_TX_CONTROL_REG			= 0x14
MFRC522_TX_AUTO_REG				= 0x15
MFRC522_CRC_RESULT_REG_H		= 0x21
MFRC522_CRC_RESULT_REG_L		= 0x22
MFRC522_T_MODE_REG 				= 0x2A
MFRC522_T_PRESCALAR_REG 		= 0x2B
MFRC522_T_RELOAD_REG_H			= 0x2C
MFRC522_T_RELOAD_REG_L			= 0x2D

class MFRC522(Device):
    OK 							= 0
    NO_TAG_ERR 					= 1
    ERR 						= 2
    CARD_REQIDL					= 0x26
    AUTH 						= 0x60

    REQIDL = 0x26
    REQALL = 0x52
    AUTHENT1A = 0x60
    AUTHENT1B = 0x61

    def __init__(self, soc_vid, spi: Spi, cs_pin: int, rst_pin: int, name: str = "mfrc522"):
        Device.__init__(self, soc_vid, {}, MFRC522_TID, name)
        self.spi = spi.pvm
        self.cs = Pin(cs_pin, Pin.OUT)
        self.cs.value(1)
        #self.spi.init()
        #self.init()
        #self.rst.value(0)

    def start(self):
        Device.start(self)
        self.init()
        return self
        

    def test_spi(self,cs_state:int=0) -> bool:
        #self.cs.value(cs_state)  # Start high (deselect)
        #try:
            #self.cs.value(1 if cs_state is 0 else 1)  # Select slave
            #data = self.spi.read(1)  # Read 1 byte 
            data_write = b'\x01'
            data_read = bytearray(1)
            self.spi.write_readinto(data_write, data_read)
            LOG.info("spi test {}/{} ({}):", 
                     str(data_write),
                     str(data_read),
                         "{{g}}good{{X}}" if data_read is not None else "{{r}}bad{{X}}")
            while True:
                (stat, tag_type) = self.request(self.REQIDL)
                if stat == self.OK:
                    (stat, raw_uid) = self.anticoll()
                    if stat is self.OK:
                        tag_id = "0x%02x".format(tag_type)
                        card_id = "0x{:02x}{:02x}{:02x}{:02x}".format(raw_uid[0], raw_uid[1], raw_uid[2], raw_uid[3])
                        LOG.info("card detected: {} : {}", tag_id, card_id)

    def _write_reg(self, reg, val):
        """Write value into register

        Parameters
        ----------
        reg : int
            Register
        val : int
            Value

        Returns
        -------
        None
        """
        self.cs.value(0)
        self.spi.write(b'%c' % int(0xff & ((reg << 1) & 0x7e)))
        self.spi.write(b'%c' % int(0xff & val))
        self.cs.value(1)

    def _read_reg(self, reg):
        """Read value from register

        Parameters
        ----------
        reg : int
            Register

        Returns
        -------
        None
        """
        self.cs.value(0)
        self.spi.write(b'%c' % int(0xff & (((reg << 1) & 0x7e) | 0x80)))
        val = self.spi.read(1)
        self.cs.value(1)
        return val[0]

    def _set_bit_mask(self, reg, mask):
        """Set the bit mask

        Parameters
        ----------
        reg : int
            Register
        mask : int
            Mask

        Returns
        -------
        None
        """
        self._write_reg(reg, self._read_reg(reg) | mask)

    def _clear_bit_mask(self, reg, mask):
        """Clear the bit mask

        Parameters
        ----------
        reg : int
            Register
        mask : int
            Mask

        Returns
        -------
        None
        """
        self._write_reg(reg, self._read_reg(reg) & (~mask))

    def _tocard(self, cmd, send):
        """To card

        Parameters
        ----------
        cmd : int
            Command
        send : int
            Send data

        Returns
        -------
        tuple
            Returns a tuple of status, recv, bits
        """
        recv = []
        bits = irq_en = wait_irq = n = 0
        status = self.ERR
        if cmd == AUTHENTICATE:
            irq_en = 0x12
            wait_irq = 0x10
        elif cmd == TRANSCEIVE:
            irq_en = 0x77
            wait_irq = 0x30
        self._write_reg(MFRC522_COML_EN_REG, irq_en | 0x80)
        self._clear_bit_mask(MFRC522_COM_IRQ_REG, 0x80)
        self._set_bit_mask(MFRC522_FIFO_LEVEL_REG, 0x80)
        self._write_reg(MFRC522_COMMAND_REG, 0x00)
        for c in send:
            self._write_reg(MFRC522_FIFO_DATA_REG, c)
        self._write_reg(MFRC522_COMMAND_REG, cmd)
        if cmd == TRANSCEIVE:
            self._set_bit_mask(MFRC522_BIT_FRAMING_REG, 0x80)
        i = 2000
        while True:
            n = self._read_reg(MFRC522_COM_IRQ_REG)
            i -= 1
            if ~((i != 0) and ~(n & 0x01) and ~(n & wait_irq)):
                break
        self._clear_bit_mask(MFRC522_BIT_FRAMING_REG, 0x80)
        if i:
            if (self._read_reg(MFRC522_ERROR_REG) & 0x1B) == 0x00:
                status = self.OK
                if n & irq_en & 0x01:
                    status = self.NO_TAG_ERR
                elif cmd == TRANSCEIVE:
                    n = self._read_reg(MFRC522_FIFO_LEVEL_REG)
                    last_bits = self._read_reg(MFRC522_CONTROL_REG) & 0x07
                    if last_bits != 0:
                        bits = (n - 1) * 8 + last_bits
                    else:
                        bits = n * 8
                    if n == 0:
                        n = 1
                    elif n > MAX_LEN:
                        n = MAX_LEN
                    for _ in range(n):
                        recv.append(self._read_reg(MFRC522_FIFO_DATA_REG))
            else:
                status = self.ERR
        return status, recv, bits

    def _calculate_crc(self, data):
        """Calculate CRC

        Parameters
        ----------
        data : int
            Data

        Returns
        -------
        int
            Returns an int read_reg
        """
        self._clear_bit_mask(MFRC522_DIV_IRQ_REG, 0x04)
        self._set_bit_mask(MFRC522_FIFO_LEVEL_REG, 0x80)
        for c in data:
            self._write_reg(MFRC522_FIFO_DATA_REG, c)
        self._write_reg(MFRC522_COMMAND_REG, CALCULATE_CRC)
        i = 0xFF
        while True:
            n = self._read_reg(MFRC522_DIV_IRQ_REG)
            i -= 1
            if not ((i != 0) and not (n & MFRC522_COM_IRQ_REG)):
                break
        return [self._read_reg(MFRC522_CRC_RESULT_REG_L), self._read_reg(MFRC522_CRC_RESULT_REG_H)]

    def init(self):
        """
        Parameters
        ----------
        None
        """
        self.reset()
        self._write_reg(MFRC522_T_MODE_REG, 0x8D)
        self._write_reg(MFRC522_T_PRESCALAR_REG, 0x3E)
        self._write_reg(MFRC522_T_RELOAD_REG_L, 30)
        self._write_reg(MFRC522_T_RELOAD_REG_H, 0)
        self._write_reg(MFRC522_TX_AUTO_REG, 0x40)
        self._write_reg(MFRC522_MODE_REG, 0x3D)
        self.antenna_on()

    def reset(self):
        """Reset

        Parameters
        ----------
        None

        Returns
        -------
        None
        """
        self._write_reg(MFRC522_COMMAND_REG, 0x0F)

    def antenna_on(self, on=True):
        """Turn antenna on

        Parameters
        ----------
        on : bool
            Antenna on/off flag

        Returns
        -------
        None
        """
        if on and ~(self._read_reg(MFRC522_TX_CONTROL_REG) & 0x03):
            self._set_bit_mask(MFRC522_TX_CONTROL_REG, 0x03)
        else:
            self._clear_bit_mask(MFRC522_TX_CONTROL_REG, 0x03)

    def request(self, mode):
        """Method to start the transmission of data

        Parameters
        ----------
        mode : list
            Mode

        Returns
        -------
        int
            Returns an int of status and bit
        """
        self._write_reg(MFRC522_BIT_FRAMING_REG, 0x07)
        (status, recv, bits) = self._tocard(MFRC522_CONTROL_REG, [mode])
        if (status != self.OK) | (bits != 0x10):
            status = self.ERR
        return status, bits

    def anticoll(self):
        """Anticoll

        Parameters
        ----------
        None

        Returns
        -------
        None
        """
        serial_number_check = 0
        serial_number = [ANTICOLL, 0x20]
        self._write_reg(MFRC522_BIT_FRAMING_REG, 0x00)
        (status, recv, bits) = self._tocard(TRANSCEIVE, serial_number)
        if status == self.OK:
            if len(recv) == 5:
                for i in range(4):
                    serial_number_check = serial_number_check ^ recv[i]
                if serial_number_check != recv[4]:
                    status = self.ERR
            else:
                status = self.ERR
        return status, recv

    def select_tag(self, serial_number):
        """Select tag

        Parameters
        ----------
        serial_number : int
            Serial number

        Returns
        -------
        bool
            Returns a bool if OK or ERR if error
        """
        buffer = [SELECT_TAG, 0x70] + serial_number[:5]
        buffer += self._calculate_crc(buffer)
        (status, recv, bits) = self._tocard(TRANSCEIVE, buffer)
        return self.OK if (status == self.OK) and (bits == 0x18) else self.ERR

    def auth(self, mode, addr, sect, serial_number):
        """Auth

        Parameters
        ----------
        mode : int
            Mode
        addr : int
            Addr
        sect : int
            Sect
        serial_number : int
            Serial number

        Returns
        -------
        bool
            Returns a bool if OK or ERR if error
        """
        return self._tocard(AUTHENTICATE, [mode, addr] + sect + serial_number[:4])[0]

    def stop_crypto1(self):
        """Stop crypto 1

        Parameters
        ----------
        None

        Returns
        -------
        None
        """
        self._clear_bit_mask(MFRC522_STATUS_2_REG, 0x08)

    def read(self, addr):
        """Read

        Parameters
        ----------
        addr : int
            Addr

        Returns
        -------
        bool
            Returns a bool if OK or None
        """
        data = [READ, addr]
        data += self._calculate_crc(data)
        (status, recv, _) = self._tocard(TRANSCEIVE, data)
        return recv if status == self.OK else None

    def write(self, addr, data):
        """Write

        Parameters
        ----------
        addr : int
            Addr
        data : bytes
            Data

        Returns
        -------
        int
            Returns an int of the status
        """
        buffer = [WRITE, addr]
        buffer += self._calculate_crc(buffer)
        (status, recv, bits) = self._tocard(TRANSCEIVE, buffer)
        if not (status == self.OK) or not (bits == 4) or not ((recv[0] & 0x0F) == 0x0A):
            status = self.ERR
        else:
            buffer = []
            for i in range(MAX_LEN):
                buffer.append(data[i])
            buffer += self._calculate_crc(buffer)
            (status, recv, bits) = self._tocard(TRANSCEIVE, buffer)
            if not (status == self.OK) or not (bits == 4) or not ((recv[0] & 0x0F) == 0x0A):
                status = self.ERR
        return status