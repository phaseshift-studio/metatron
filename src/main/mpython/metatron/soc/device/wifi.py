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

from metatron.obj import Int
from metatron.soc.device.device import Device
from metatron.util.furi import f
from metatron.util.graphitty import LOG
from metatron.util.homeassistant import HomeAssistant
from metatron.util.mach import router


class Wifi(Device):
    def __init__(self, ssid:str,password:str,host:str):
        self.ha = None
        self.wlan = network.WLAN(network.STA_IF)
        self.wlan.active(True)
        self.wlan.config(dhcp_hostname=host)
        self.wlan.connect(ssid, password)
        LOG.info("connecting to {{y}}{}{{X}} wifi", ssid)
        while not self.wlan.isconnected():
            print('.',end="")
            pass
        print("")
        LOG.info("connected to {{y}}{}{{X}} as {{y}}{}\n\t{}",ssid, self.wlan.config('hostname'), str(self.wlan.ifconfig()))  
        Device.__init__(self,f(self.host()),{},f("/soc/device/wifi"),"wifi")
   
    def ipaddr(self) -> str:
        return self.wlan.ifconfig()[0]

    def host(self) -> str:
        return self.wlan.config('hostname')
    
    def strength(self):
        return self.wlan.status('rssi')