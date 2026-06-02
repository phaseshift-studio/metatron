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
import gc
import network
import time

from metatron.furi import f
from metatron.soc.device.device import Device
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

    def reconnect(self, ssid: str, password: str, host: str):
        max_retries = 10
        self.stop()
        for attempt in range(max_retries):
            try:
                LOG.info(f"reconnection attempt {attempt + 1} out of {max_retries}")
                time.sleep(1 * (2 ** attempt))  # exponential backoff
                gc.collect()
                self.wlan = Wifi.connect(ssid, password, host)
                return
            except Exception as e:
                pass

    def __init__(self, wlan: network.WLAN, secrets:dict, soc_vid, name: str = "wifi"):
        Device.__init__(self, soc_vid=soc_vid, pvm={}, tid=WIFI_TID, name=name)
        self.wlan = wlan
        self.ssid = secrets['ssid']
        self.password = secrets['password']
        self.host = secrets['host']
        self.last_report = None

    def ipaddr(self) -> str:
        return self.wlan.ifconfig()[0]

    def host(self) -> str:
        return self.wlan.config('hostname')

    def strength(self):
        return self.wlan.status('rssi')
    
    def loop(self):
        if self.soc_vid is not None and (self.last_report is None or (time.time() - self.last_report) > 10):
            self.last_report = time.time()
            router().write(self.soc_vid.extend(self.name).extend("state"), network.WLAN().status('rssi'))

    def start(self) -> 'Wifi':
        if self.wlan is None or not self.wlan.isconnected():
            self.wlan = Wifi.connect(self.ssid, self.password, self.host)
        return self

    def stop(self):
        Device.stop(self)
        self.wlan.close()
