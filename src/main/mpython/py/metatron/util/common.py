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

import json

from metatron.util.graphitty import LOG


def load_secrets(file="secrets.json"):
    try:
        secrets = json.load(open(file))
        return secrets
    except FileNotFoundError:
        LOG.error("{} not found", file)
    except json.JSONDecodeError as e:
        LOG.error("{} is invalid json: {}", file, e)
    except Exception as e:
        LOG.error("unexpected error loading {}: {}", file, e)
        
def make_pwm_read_lambda(index):
    return lambda s: s.pwm[index]

def make_pwm_write_lambda(index):
    return lambda s, v: s.pwm.__setitem__(index, v)

def make_gpio_read_lambda(index):
    return lambda s: s.gpio[index]

def make_gpio_write_lambda(index):
    return lambda s, v: s.gpio.__setitem__(index, v)