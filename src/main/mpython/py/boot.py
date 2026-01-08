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

import _thread
import esp
import gc
import json
import machine
import network
import os
import sys
import time

import metatron.util.graphitty as graphitty
from arch.deploy import deploy
from metatron.router import Router
from metatron.util.common import load_secrets
from metatron.util.graphitty import LOG
from metatron.util.mach import mach
from metatron.util.translators import PythonTranslator

esp.osdebug(None)
###############################################################################
sys.ps1 = graphitty.string("{{m}}mtron{{g}}>{{X}} ")
sys.ps2 = graphitty.string("{{m}}     {{g}}>{{X}} ")
print(graphitty.string("""
{{g}}        /^\/^\                                                     
{{g}}      _|__|  {{w}}O{{g}}|                                                    
{{r}}\/ {{g}} /{{y}}~{{g}}     \_/ \                                                  
{{r}} \_{{g}}|__________/ \  {{y}}{{~}}PhaseShift Studio Presents{{X}}                                                 
{{g}}     \_______    \               __        __                   
{{g}}             `\   \__ ___  ___  / /_____ _{{y}}/ /__________  ____   
{{g}}              |   __ `__ \/ _ \/ {{y}}__/ __ `/ __/ ___/ __ \/ __ \  
{{g}}             /   / / / / {{c}}/  __/ /_/ /_/ / /_/ /  / /_/ / / / /  
{{g}}            /___/ {{b}}/_/ /_/\___/\__/\__,_/\__/_/   \____/_/ /_/{{X}}"""))
print(graphitty.string("\t\t\t{{b}}on {}{{X}}\n", os.uname().machine))
###############################################################################
mach["router"] = Router()
mach["translator"] = PythonTranslator()

secrets = load_secrets("secrets.json")
LOG.log_level = secrets.get("log", "info")
LOG.info("log level {{g}}{}{{X}}", LOG.log_level)
mtron = deploy(secrets)


def main_thread_function():
    global mtron
    gc.collect()
    LOG.none("\n")
    LOG.info("{{y}}{}{{X}} boot process complete", mtron.soc.vid)
    while True:
        try:
            mtron.loop()
        except Exception as ex:
            print("resetting due to unhandled main loop error", ex)
            machine.reset()

if "stack_kb" in secrets.keys():
    _thread.stack_size(secrets["stack_kb"] * 1024)
_thread.start_new_thread(main_thread_function, ())
