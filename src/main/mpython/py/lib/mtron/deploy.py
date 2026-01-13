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
from lib.mtron.walltron import Walltron


# this function determines which machine architecture is flashed to hardware
# update this function accordingly with calls to constructors from different
# architectures in arch

######################################################################
# IMPORTANT: do not change the signature of the deploy() and always
# return an Architecture object with a loop() method
def deploy(secrets: dict):
    return Walltron(secrets)
