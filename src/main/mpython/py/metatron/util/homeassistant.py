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

from lib import uhome

from metatron.soc.soc import SoC
from metatron.util.furi import fURI, f
from metatron.util.graphitty import LOG
from metatron.util.mach import router
from metatron.util.translators import JSONTranslator


class HomeAssistant:
    def __init__(self, soc: SoC, prefix='homeassistant'):
        self.soc = soc
        self.device = uhome.Device(soc.vid.name(), discovery_prefix=prefix)
        self.entities = {}

    def connect(self):
        LOG.info("connecting to {{b}}HomeAssistant{{X}} via {{c}}MQTT{{X}}")
        self.device.connect(router().get_space(self.soc.vid).client)

    def register(self, entity_vid):
        return _Form(entity_vid, self)

    def update(self, entity_pattern: fURI = f("#")):
        for k, v in self.entities.items():
            if k.matches(entity_pattern):
                v[0].publish(JSONTranslator.from_obj(v[1](self.soc)))

    def announce(self):
        self.device.discover_all()

    def loop(self):
        self.device.loop()
        # self.update()


class _Form:
    def __init__(self, entity_vid, ha: HomeAssistant):
        self.entity_vid = entity_vid
        self.ha = ha

    def number(self):
        return _Builder(self.ha, self.entity_vid, "number")

    def sensor(self):
        return _Builder(self.ha, self.entity_vid, "sensor")


class _Builder:
    entity_vid = None
    write_f = None
    read_f = None
    kind = None
    ha = None
    settings = {}

    def __init__(self, ha: HomeAssistant, entity_vid, kind: str):
        self.ha = ha
        self.kind = kind
        self.entity_vid = entity_vid

    def primary(self) -> '_Builder':
        return self

    def diagnostic(self) -> '_Builder':
        self.settings['entity_category'] = 'diagnostic'
        return self

    def config(self) -> '_Builder':
        self.settings['entity_category'] = 'config'
        return self

    def on_read(self, func: function) -> '_Builder':
        self.read_f = func
        return self

    def on_write(self, func: function) -> '_Builder':
        self.write_f = func
        return self

    def mode(self, mode: str) -> '_Builder':
        self.settings['mode'] = mode
        return self

    def icon(self, icon) -> '_Builder':
        self.settings['icon'] = icon
        return self

    def min_max(self, minimum: int, maximum: int) -> '_Builder':
        self.settings['min'] = minimum
        self.settings['max'] = maximum
        return self

    def unit_of_measurement(self, uofm: str) -> '_Builder':
        self.settings['unit_of_measurement'] = uofm
        return self

    def device_class(self, dc: str) -> '_Builder':
        self.settings['device_class'] = dc
        return self

    def create(self):
        LOG.info("registering {{y}}{}{{X}} as a {{b}}{} {{c}}{}", self.entity_vid, self.kind,
                 self.settings['entity_category'])
        if self.kind == "sensor":
            self.ha.entities[self.entity_vid] = [uhome.Sensor(self.ha.device, self.entity_vid.name(), **self.settings),
                                                 self.read_f, self.write_f]
        elif self.kind == "number":
            entity = uhome.Number(self.ha.device, self.entity_vid.name(), **self.settings)
            if self.write_f is not None:
                entity.set_action(lambda v: self.write_f(self.ha.soc, JSONTranslator.to_obj(v)))
            self.ha.entities[self.entity_vid] = [entity, self.read_f, self.write_f]
        else:
            raise RuntimeError(f'{self.kind} is current not supported')
