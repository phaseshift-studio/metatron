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
import network

from metatron.soc.device.device import Device
from metatron.util.furi import f
from metatron.util.graphitty import LOG
from metatron.util.mach import router

WIFI_TID = f("/soc/wifi")


class Wifi(Device):
    @staticmethod
    def connect(ssid: str, password: str, host: str):
        LOG.info("connecting to {{y}}{}{{X}} wifi", ssid)
        wlan = network.WLAN(network.STA_IF)
        wlan.active(True)
        network.hostname(host)
        wlan.connect(ssid, password)
        while not wlan.isconnected():
            pass
        LOG.info("connected to {{y}}{}{{X}} as {{y}}{}.local\n\t{}", ssid, wlan.config('hostname'),
                 str(wlan.ifconfig()))
        return wlan

    def __init__(self, wlan: network.WLAN, soc_vid, name: str = "wifi"):
        Device.__init__(self, soc_vid, {}, WIFI_TID, name)
        self.wlan = wlan
        if self.soc_vid is not None:
            router().write(self.soc_vid.extend(name).extend("state"), network.WLAN().status('rssi'))

    def ipaddr(self) -> str:
        return self.wlan.ifconfig()[0]

    def host(self) -> str:
        return self.wlan.config('hostname')

    def strength(self):
        return self.wlan.status('rssi')
